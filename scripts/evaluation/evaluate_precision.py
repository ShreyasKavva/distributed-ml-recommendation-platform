"""
Evaluates the recommendation engine's precision@K against held-out review
data. Run this after ingesting data and starting the app - the number it
prints is a real, reproducible measurement, not a placeholder.

IMPORTANT - two-phase process, and why:
A naive version of this script (an earlier draft of this file) called the
live API directly using the full, already-ingested review history. That
produces a false ~0% precision reading: RecommendationService correctly
excludes businesses a user has already reviewed, and if the database
already contains a user's "future" (held-out) reviews, the system will
never recommend those businesses back - not because the recommender is
broken, but because a temporal split needs to happen at the DATA layer,
not just bookkept in an evaluation script.

So this script has two phases:
  1. --prepare-split: reads the full reviews file, splits each user's
     reviews chronologically (earliest ~80% = train, most recent ~20% =
     test), and writes TWO files: a train-only reviews file (which you
     ingest instead of the full one) and a test-holdout file (kept aside
     as ground truth - never ingested).
  2. --measure: calls the live API (which only knows about the train
     data) and checks whether its recommendations land in each user's
     held-out "future" likes.

Usage:
    pip install -r requirements.txt

    # Phase 1: prepare the split BEFORE ingesting
    python evaluate_precision.py --prepare-split \
        --reviews ../../data/reviews.json --output-dir ../../data/eval

    # Then ingest data/eval/reviews_train.json (rename/copy it to reviews.json
    # in the directory you point --app.ingestion.data-path at, alongside the
    # full, unsplit businesses.json) and start the app.

    # Phase 2: measure against the live, train-only-ingested API
    python evaluate_precision.py --measure \
        --test-holdout ../../data/eval/test_holdout.json \
        --api-base http://localhost:8080 --k 10
"""

import argparse
import json
import random
import statistics
from collections import defaultdict

import requests


def load_reviews(path):
    reviews_by_user = defaultdict(list)
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            record = json.loads(line)
            reviews_by_user[record["user_id"]].append(record)
    return reviews_by_user


def split_train_test(reviews, test_fraction=0.2):
    reviews_sorted = sorted(reviews, key=lambda r: r["date"])
    split_index = max(1, int(len(reviews_sorted) * (1 - test_fraction)))
    return reviews_sorted[:split_index], reviews_sorted[split_index:]


def relevant_businesses(test_reviews, min_stars=4.0):
    return {r["business_id"] for r in test_reviews if r["stars"] >= min_stars}


def precision_at_k(recommended_ids, relevant_ids, k):
    if not recommended_ids:
        return 0.0
    top_k = recommended_ids[:k]
    hits = sum(1 for biz_id in top_k if biz_id in relevant_ids)
    return hits / min(k, len(top_k))


def fetch_recommendations(api_base, user_id, k):
    response = requests.get(
        f"{api_base}/api/v1/recommendations/{user_id}",
        params={"topK": k},
        timeout=10,
    )
    response.raise_for_status()
    payload = response.json()
    return [item["business"]["id"] for item in payload]


def cmd_prepare_split(args):
    import os

    os.makedirs(args.output_dir, exist_ok=True)
    reviews_by_user = load_reviews(args.reviews)

    train_path = os.path.join(args.output_dir, "reviews_train.json")
    holdout_path = os.path.join(args.output_dir, "test_holdout.json")

    train_count = 0
    holdout_count = 0

    with open(train_path, "w", encoding="utf-8") as train_f, \
         open(holdout_path, "w", encoding="utf-8") as holdout_f:
        for user_id, reviews in reviews_by_user.items():
            train, test = split_train_test(reviews, args.test_fraction)
            for r in train:
                train_f.write(json.dumps(r) + "\n")
                train_count += 1
            for r in test:
                holdout_f.write(json.dumps(r) + "\n")
                holdout_count += 1

    print(f"Wrote {train_count:,} train reviews to {train_path}")
    print(f"Wrote {holdout_count:,} held-out reviews to {holdout_path}")
    print()
    print("Next: copy/rename reviews_train.json to reviews.json in your")
    print("ingestion data directory (alongside the full businesses.json),")
    print("ingest it, start the app, then run this script with --measure.")


def cmd_measure(args):
    holdout_by_user = load_reviews(args.test_holdout)
    eligible_users = [u for u, revs in holdout_by_user.items() if len(revs) >= 1]

    if not eligible_users:
        print("No users with held-out reviews found - check --test-holdout path.")
        return

    random.seed(args.seed)
    sample = random.sample(eligible_users, min(args.sample_users, len(eligible_users)))

    scores = []
    skipped = 0
    for user_id in sample:
        relevant = relevant_businesses(holdout_by_user[user_id])
        if not relevant:
            continue
        try:
            recommended = fetch_recommendations(args.api_base, user_id, args.k)
        except requests.RequestException:
            skipped += 1
            continue
        scores.append(precision_at_k(recommended, relevant, args.k))

    if not scores:
        print("No scoreable users - check the API is running against the TRAIN-only ingested data.")
        return

    mean_precision = statistics.mean(scores)
    print(f"Evaluated {len(scores)} users ({skipped} skipped due to request errors)")
    print(f"Mean Precision@{args.k}: {mean_precision:.3f} ({mean_precision * 100:.1f}%)")
    print(f"Users with >=1 hit in top-{args.k}: {sum(1 for s in scores if s > 0)}/{len(scores)}")


def main():
    parser = argparse.ArgumentParser(description="Two-phase precision@K evaluation")
    parser.add_argument("--prepare-split", action="store_true", help="Phase 1: split reviews into train/holdout files")
    parser.add_argument("--measure", action="store_true", help="Phase 2: measure precision against a live, train-ingested API")

    parser.add_argument("--reviews", help="[prepare-split] Path to the full reviews.json")
    parser.add_argument("--output-dir", default="./eval-data", help="[prepare-split] Where to write the split files")
    parser.add_argument("--test-fraction", type=float, default=0.2, help="[prepare-split] Fraction of each user's reviews to hold out")

    parser.add_argument("--test-holdout", help="[measure] Path to test_holdout.json from phase 1")
    parser.add_argument("--api-base", default="http://localhost:8080", help="[measure] Base URL of the running API")
    parser.add_argument("--k", type=int, default=10)
    parser.add_argument("--sample-users", type=int, default=300)
    parser.add_argument("--seed", type=int, default=42)

    args = parser.parse_args()

    if args.prepare_split:
        if not args.reviews:
            parser.error("--prepare-split requires --reviews")
        cmd_prepare_split(args)
    elif args.measure:
        if not args.test_holdout:
            parser.error("--measure requires --test-holdout")
        cmd_measure(args)
    else:
        parser.error("Specify --prepare-split or --measure")


if __name__ == "__main__":
    main()
