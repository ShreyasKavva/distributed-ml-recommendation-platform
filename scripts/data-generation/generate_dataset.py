"""
Generates a realistic, synthetic restaurant/business/review dataset in the
same schema the ingestion pipeline expects - no external download, no
license, no ToS to worry about. It's not real Yelp data; it's built to have
the same *structure* real review-platform data has, so the recommendation
algorithm has genuine signal to learn from:

  - Businesses cluster into ~40 realistic category "archetypes" (e.g.
    Italian+Pizza, Japanese+Sushi Bars) rather than random category soup.
  - Each user has 1-3 preferred archetypes ("taste"), and ~75% of their
    reviews go to businesses matching that taste (rated higher on average),
    with the rest being exploration - this gives content-based and
    collaborative filtering something real to find.
  - Review counts and ratings follow skewed distributions similar to real
    review platforms (most businesses have few reviews, a handful have many;
    ratings cluster toward 4-5 stars).

Output is line-delimited JSON, one file for businesses and one for reviews,
in the exact field names DataIngestionService.java already parses.

Usage:
    python generate_dataset.py --businesses 500000 --users 25000 --output-dir ../../data
    python generate_dataset.py --businesses 5000 --users 500 --output-dir ../../data/sample
"""

import argparse
import json
import random
from datetime import datetime, timedelta

import numpy as np

CITIES = [
    ("Philadelphia", "PA", 39.9526, -75.1652), ("Tucson", "AZ", 32.2226, -110.9747),
    ("Tampa", "FL", 27.9506, -82.4572), ("Indianapolis", "IN", 39.7684, -86.1581),
    ("Nashville", "TN", 36.1627, -86.7816), ("New Orleans", "LA", 29.9511, -90.0715),
    ("Portland", "OR", 45.5152, -122.6784), ("Austin", "TX", 30.2672, -97.7431),
    ("Charlotte", "NC", 35.2271, -80.8431), ("Denver", "CO", 39.7392, -104.9903),
    ("Columbus", "OH", 39.9612, -82.9988), ("Sacramento", "CA", 38.5816, -121.4944),
    ("Kansas City", "MO", 39.0997, -94.5786), ("Atlanta", "GA", 33.7490, -84.3880),
    ("Pittsburgh", "PA", 40.4406, -79.9959), ("St. Louis", "MO", 38.6270, -90.1994),
    ("Cincinnati", "OH", 39.1031, -84.5120), ("Milwaukee", "WI", 43.0389, -87.9065),
    ("Raleigh", "NC", 35.7796, -78.6382), ("Salt Lake City", "UT", 40.7608, -111.8910),
]

# (archetype label, category list) - category order matters for realism but not for parsing
ARCHETYPES = [
    ("italian", ["Restaurants", "Food", "Italian", "Pizza"]),
    ("italian_fine", ["Restaurants", "Food", "Italian", "Wine Bars"]),
    ("mexican", ["Restaurants", "Food", "Mexican", "Tacos"]),
    ("mexican_fast", ["Restaurants", "Food", "Mexican", "Fast Food"]),
    ("chinese", ["Restaurants", "Food", "Chinese"]),
    ("japanese_sushi", ["Restaurants", "Food", "Japanese", "Sushi Bars"]),
    ("thai", ["Restaurants", "Food", "Thai"]),
    ("indian", ["Restaurants", "Food", "Indian"]),
    ("korean", ["Restaurants", "Food", "Korean", "Korean BBQ"]),
    ("vietnamese", ["Restaurants", "Food", "Vietnamese"]),
    ("mediterranean", ["Restaurants", "Food", "Mediterranean"]),
    ("middle_eastern", ["Restaurants", "Food", "Middle Eastern"]),
    ("greek", ["Restaurants", "Food", "Greek"]),
    ("american_trad", ["Restaurants", "Food", "American (Traditional)", "Diners"]),
    ("american_new", ["Restaurants", "Food", "American (New)"]),
    ("burgers", ["Restaurants", "Food", "Burgers", "Fast Food"]),
    ("steakhouse", ["Restaurants", "Food", "Steakhouses"]),
    ("seafood", ["Restaurants", "Food", "Seafood"]),
    ("bbq", ["Restaurants", "Food", "Barbeque"]),
    ("pizza", ["Restaurants", "Food", "Pizza"]),
    ("sandwiches", ["Restaurants", "Food", "Sandwiches", "Delis"]),
    ("cafe", ["Restaurants", "Food", "Cafes", "Coffee & Tea"]),
    ("coffee", ["Food", "Coffee & Tea", "Cafes"]),
    ("bakery", ["Food", "Bakeries", "Coffee & Tea"]),
    ("breakfast", ["Restaurants", "Food", "Breakfast & Brunch"]),
    ("bar", ["Restaurants", "Food", "Bars", "American (Traditional)"]),
    ("sports_bar", ["Restaurants", "Food", "Bars", "American (New)"]),
    ("vegan", ["Restaurants", "Food", "Vegan", "Vegetarian"]),
    ("food_truck", ["Food", "Food Trucks"]),
    ("fast_food", ["Restaurants", "Food", "Fast Food"]),
]

NAME_PREFIXES = [
    "Golden", "Blue", "Green", "Red", "Silver", "Sunset", "Riverside", "Downtown",
    "Corner", "Uptown", "Little", "Old Town", "Northside", "Southside", "Main Street",
    "Bella", "Casa", "El", "La", "The", "Royal", "Grand", "Urban", "Copper", "Maple",
]

NAME_SUFFIXES = [
    "Kitchen", "Bistro", "Grill", "Cafe", "Diner", "House", "Eatery", "Bar & Grill",
    "Restaurant", "Table", "Spot", "Pantry", "Corner", "Kitchen & Bar", "Cantina",
    "Tavern", "Provisions", "Joint", "Room",
]


def weighted_archetype_indices(n, rng):
    # A handful of archetypes (pizza, mexican, american, cafe, bar) are just more
    # common in the real world than e.g. Korean BBQ - mild popularity skew.
    weights = np.ones(len(ARCHETYPES))
    common = ["pizza", "mexican_fast", "american_trad", "cafe", "bar", "fast_food", "burgers"]
    for i, (label, _) in enumerate(ARCHETYPES):
        if label in common:
            weights[i] = 2.5
    weights = weights / weights.sum()
    return rng.choice(len(ARCHETYPES), size=n, p=weights)


def generate_businesses(n, rng, py_random):
    print(f"Generating {n:,} businesses...")
    archetype_idx = weighted_archetype_indices(n, rng)
    city_idx = rng.integers(0, len(CITIES), size=n)

    # Power-law-ish review counts: most businesses have few reviews, a few have many
    review_counts = np.round(np.random.default_rng(rng.integers(1e9)).pareto(1.8, size=n) * 8 + 3).astype(int)
    review_counts = np.clip(review_counts, 1, 4000)

    # Ratings skew toward 3.5-4.5, like real review platforms
    stars_raw = np.random.default_rng(rng.integers(1e9)).beta(8, 3, size=n) * 5
    stars = np.round(stars_raw * 2) / 2.0  # round to nearest 0.5
    stars = np.clip(stars, 1.0, 5.0)

    is_open = rng.random(n) < 0.87
    lat_jitter = rng.normal(0, 0.08, size=n)
    lon_jitter = rng.normal(0, 0.08, size=n)

    businesses = []
    business_ids_by_archetype = {i: [] for i in range(len(ARCHETYPES))}

    for i in range(n):
        biz_id = f"biz-{i:07d}"
        arch_i = int(archetype_idx[i])
        city, state, lat, lon = CITIES[int(city_idx[i])]
        name = f"{py_random.choice(NAME_PREFIXES)} {py_random.choice(NAME_SUFFIXES)}"

        businesses.append({
            "business_id": biz_id,
            "name": name,
            "address": f"{100 + (i % 899)} {py_random.choice(['Main St', 'Oak Ave', 'Elm St', 'Broadway', 'Market St', '1st Ave'])}",
            "city": city,
            "state": state,
            "postal_code": f"{10000 + (i % 89999)}",
            "latitude": round(lat + float(lat_jitter[i]), 6),
            "longitude": round(lon + float(lon_jitter[i]), 6),
            "stars": float(stars[i]),
            "review_count": int(review_counts[i]),
            "is_open": bool(is_open[i]),
            "categories": ",".join(ARCHETYPES[arch_i][1]),
        })
        business_ids_by_archetype[arch_i].append(biz_id)

        if (i + 1) % 100_000 == 0:
            print(f"  ...{i + 1:,} businesses generated")

    return businesses, business_ids_by_archetype


def generate_reviews(num_users, avg_reviews_per_user, business_ids_by_archetype, rng, py_random):
    all_business_ids = [bid for ids in business_ids_by_archetype.values() for bid in ids]
    num_archetypes = len(ARCHETYPES)

    print(f"Generating reviews for {num_users:,} users (avg {avg_reviews_per_user}/user)...")
    review_counts_per_user = np.clip(
        np.random.default_rng(rng.integers(1e9)).lognormal(mean=np.log(avg_reviews_per_user), sigma=0.6, size=num_users).astype(int),
        1, 300
    )

    start_date = datetime(2022, 1, 1)
    date_range_days = (datetime(2026, 8, 1) - start_date).days

    reviews = []
    review_counter = 0

    for u in range(num_users):
        user_id = f"user-{u:06d}"
        # 1-3 preferred archetypes = this user's "taste"
        num_prefs = py_random.choices([1, 2, 3], weights=[0.5, 0.35, 0.15], k=1)[0]
        preferred_archetypes = py_random.sample(range(num_archetypes), num_prefs)
        preferred_pool = [bid for a in preferred_archetypes for bid in business_ids_by_archetype[a]]
        if not preferred_pool:
            preferred_pool = all_business_ids

        n_reviews = int(review_counts_per_user[u])
        # Timestamps for this user's reviews, sorted so "recent" reviews are meaningful
        offsets = sorted(py_random.sample(range(date_range_days), min(n_reviews, date_range_days)))

        for offset in offsets:
            on_profile = py_random.random() < 0.75
            business_id = py_random.choice(preferred_pool if on_profile else all_business_ids)

            if on_profile:
                stars = min(5, max(1, round(py_random.betavariate(6, 2) * 4 + 1)))
            else:
                stars = min(5, max(1, round(py_random.betavariate(2.5, 2.5) * 4 + 1)))

            review_date = start_date + timedelta(days=offset, hours=py_random.randint(0, 23))
            reviews.append({
                "review_id": f"rev-{review_counter:08d}",
                "user_id": user_id,
                "business_id": business_id,
                "stars": float(stars),
                "date": review_date.strftime("%Y-%m-%d %H:%M:%S"),
                "useful": py_random.randint(0, 15),
            })
            review_counter += 1

        if (u + 1) % 5_000 == 0:
            print(f"  ...{u + 1:,} users processed, {review_counter:,} reviews so far")

    return reviews


def write_jsonl(records, path):
    with open(path, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r) + "\n")


def main():
    parser = argparse.ArgumentParser(description="Generate synthetic restaurant/review data")
    parser.add_argument("--businesses", type=int, default=500_000)
    parser.add_argument("--users", type=int, default=25_000)
    parser.add_argument("--avg-reviews-per-user", type=int, default=15)
    parser.add_argument("--output-dir", default="../../data")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    import os
    os.makedirs(args.output_dir, exist_ok=True)

    rng = np.random.default_rng(args.seed)
    py_random = random.Random(args.seed)

    t0 = datetime.now()
    businesses, business_ids_by_archetype = generate_businesses(args.businesses, rng, py_random)
    t1 = datetime.now()
    print(f"Businesses generated in {(t1 - t0).total_seconds():.1f}s")

    reviews = generate_reviews(args.users, args.avg_reviews_per_user, business_ids_by_archetype, rng, py_random)
    t2 = datetime.now()
    print(f"Reviews generated in {(t2 - t1).total_seconds():.1f}s")

    biz_path = os.path.join(args.output_dir, "businesses.json")
    rev_path = os.path.join(args.output_dir, "reviews.json")
    write_jsonl(businesses, biz_path)
    write_jsonl(reviews, rev_path)
    t3 = datetime.now()

    print(f"\nWrote {len(businesses):,} businesses to {biz_path}")
    print(f"Wrote {len(reviews):,} reviews to {rev_path}")
    print(f"Write time: {(t3 - t2).total_seconds():.1f}s | Total: {(t3 - t0).total_seconds():.1f}s")


if __name__ == "__main__":
    main()
