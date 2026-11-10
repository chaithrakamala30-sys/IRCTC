import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 25 }, // Ramp up to 25 VUs
    { duration: '1m', target: 50 },  // Hold at 50 VUs
    { duration: '30s', target: 0 },  // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<800'],
    http_req_failed: ['rate<0.02'],
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  // Station search / autocomplete simulation
  const resStations = http.get(`${BASE_URL}/api/v1/public/stations?query=DEL`);
  check(resStations, {
    'station search status is 200': (r) => r.status === 200,
  });

  sleep(1);

  // Train search simulation
  const resSearch = http.get(`${BASE_URL}/api/v1/public/trains/search?from=NDLS&to=MMCT&date=2026-12-01`);
  check(resSearch, {
    'train search status is 200': (r) => r.status === 200,
  });

  sleep(2);
}

