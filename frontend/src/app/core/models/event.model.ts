export interface ZoneEventResponse {
  id: string;
  zoneId: string;
  zoneName: string;
  eventType: 'ENTER' | 'EXIT';
  timestamp: string;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
}

export interface BulkUploadResponse {
  totalRows: number;
  importedCount: number;
  skippedCount: number;
  errors: BulkUploadError[];
}

export interface BulkUploadError {
  row: number;
  line: string;
  reason: string;
}
