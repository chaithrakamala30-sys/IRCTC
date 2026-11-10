import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 40 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500'], // High-concurrency reservations under 1.5s
    http_req_failed: ['rate<0.05'],
  },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';

export default function () {
  // Simulate concurrent fare inquiry before reservation
  const quotePayload = JSON.stringify({
    trainNumber: '12952',
    originStationCode: 'NDLS',
    destinationStationCode: 'MMCT',
    travelClass: '3A',
    journeyDate: '2026-11-20',
    passengerCategory: 'GENERAL',
  });

  const res = http.post(`${BASE_URL}/api/v1/public/fares/quotes`, quotePayload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'quote processed': (r) => r.status === 200 || r.status === 400 || r.status === 404,
  });

  sleep(0.5);
}

