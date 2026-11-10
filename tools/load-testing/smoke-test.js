import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '15s',
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.01'],    // less than 1% errors
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  // 1. Health endpoint
  const resHealth = http.get(`${BASE_URL}/actuator/health`);
  check(resHealth, {
    'health status is 200': (r) => r.status === 200,
  });

  // 2. Public Fare Quote
  const payload = JSON.stringify({
    trainNumber: '12002',
    originStationCode: 'NDLS',
    destinationStationCode: 'BPL',
    travelClass: '2A',
    journeyDate: '2026-12-01',
    passengerCategory: 'GENERAL',
  });

  const resFare = http.post(`${BASE_URL}/api/v1/public/fares/quotes`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(resFare, {
    'fare quote status is 200 or 404': (r) => r.status === 200 || r.status === 404,
  });

  sleep(1);
}
