import http from 'k6/http';
import { check, sleep } from 'k6';

// Simulates a traffic surge against the recommendations endpoint so you can
// watch the HorizontalPodAutoscaler react in real time. In another terminal:
//   kubectl get hpa rec-platform-api-hpa -n rec-platform --watch
//
// Run with:
//   k6 run --env API_BASE=http://<service-host> scripts/load-test/loadtest.js

const API_BASE = __ENV.API_BASE || 'http://localhost:8080';
const SAMPLE_USER_IDS = (__ENV.USER_IDS || 'user-1,user-2,user-3,user-4,user-5').split(',');

export const options = {
  stages: [
    { duration: '1m', target: 20 },   // baseline load
    { duration: '2m', target: 20 },   // hold baseline
    { duration: '1m', target: 200 },  // ~10x surge
    { duration: '3m', target: 200 },  // hold surge - watch the HPA scale pods
    { duration: '2m', target: 20 },   // ramp back down
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],    // this is your uptime/success-rate signal
    http_req_duration: ['p(95)<800'],
  },
};

export default function () {
  const userId = SAMPLE_USER_IDS[Math.floor(Math.random() * SAMPLE_USER_IDS.length)];
  const res = http.get(`${API_BASE}/api/v1/recommendations/${userId}?topK=10`);
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
