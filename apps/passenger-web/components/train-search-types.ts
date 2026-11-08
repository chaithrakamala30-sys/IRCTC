export type StationSuggestion = {
  stationId: string;
  stationCode: string;
  stationName: string;
  city: string;
  state: string;
  timezone: string;
};

export type CoachClass = {
  code: string;
  name: string;
  coachCount: number;
  coachTypes: string[];
};

export type TrainSearchResult = {
  trainId: string;
  trainNumber: string;
  trainName: string;
  categoryCode: string;
  categoryName: string;
  origin: StationSuggestion;
  destination: StationSuggestion;
  journeyDate: string;
  arrivalDate: string;
  departureAt: string;
  arrivalAt: string;
  durationMinutes: number;
  distanceKm: number;
  intermediateStops: number;
  operatingDays: number[];
  coachClasses: CoachClass[];
};

export type RouteStop = {
  stationId: string;
  stationCode: string;
  stationName: string;
  city: string;
  timezone: string;
  sequence: number;
  arrivalTime: string | null;
  departureTime: string | null;
  arrivalDate: string | null;
  departureDate: string | null;
  arrivalDayOffset: number;
  departureDayOffset: number;
  haltMinutes: number;
  distanceFromSourceKm: number;
  selectedOrigin: boolean;
  selectedDestination: boolean;
};

export type TrainDetail = {
  journey: TrainSearchResult;
  route: RouteStop[];
};

export type SearchPage<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
};

export type SearchFilters = {
  trainCategories: { code: string; name: string }[];
  coachClasses: CoachClass[];
};

export type SearchRequest = {
  fromStationId: string;
  toStationId: string;
  journeyDate: string;
  trainCategory?: string;
  coachClass?: string;
  departureFrom?: string;
  departureTo?: string;
  arrivalFrom?: string;
  arrivalTo?: string;
  maximumDuration?: number;
  q?: string;
  sortBy: string;
};
