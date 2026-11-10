# Phase 13 REST API Reference

This document details the REST APIs implemented for RailConnect's Railway Operations & Control Centre.

---

## 1. Public Tracking API

### Get Live Train Tracking
- **GET** `/api/v1/tracking/trains/{trainNumber}`
- **Parameters**: `serviceDate` (optional, defaults to latest run)
- **Response**: `200 OK`
  ```json
  {
    "trainNumber": "12301",
    "trainName": "Howrah Rajdhani",
    "serviceDate": "2026-11-10",
    "operationalStatus": "DELAYED",
    "currentDelayMinutes": 25,
    "routeProgressPercent": 40.0,
    "currentStationCode": "CNB",
    "currentStationName": "Kanpur Central",
    "dataReliabilityStatus": "STAFF_VERIFIED",
    "stops": [
      {
        "stopIndex": 0,
        "stationCode": "NDLS",
        "stationName": "New Delhi",
        "scheduledDeparture": "16:50",
        "actualDeparture": "16:50",
        "delayMinutes": 0,
        "platformNumber": "1",
        "stopStatus": "COMPLETED"
      }
    ]
  }
  ```

---

## 2. Operations Control Centre APIs

- **GET** `/api/v1/operations/dashboard`: Aggregated dashboard summary with active runs, delayed counters, and incidents.
- **GET** `/api/v1/operations/train-runs`: Day-of-service train telemetry.
- **POST** `/api/v1/operations/station-updates`: Record arrival/departure timestamps.
- **GET** `/api/v1/operations/stations/{stationId}/platforms`: List platform statuses.
- **POST** `/api/v1/operations/platform-assignments`: Assign platform slot with conflict detection.
- **GET/POST** `/api/v1/operations/incidents`: Log and list control centre incidents.
- **POST** `/api/v1/operations/disruptions`: Report disruptions and trigger passenger impact assessments.
- **GET/POST** `/api/v1/operations/announcements`: Publish operational broadcasts.

