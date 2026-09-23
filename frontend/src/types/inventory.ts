export const statusLabels = {
  ACTIVE: "Active",
  UNDER_REPAIR: "Under Repair",
  FOR_REPAIR: "For Repair",
  STORAGE: "Storage",
  RETIRED: "Retired",
  DISPOSED: "Disposed",
} as const;
export type PrinterStatus = keyof typeof statusLabels;
export interface Location {
  id: number;
  department: string;
  section: string | null;
  building: string | null;
  floor: string | null;
  room: string | null;
  description: string | null;
  version: number;
}
export interface Printer {
  id: number;
  brand: string;
  model: string;
  serialNumber: string | null;
  stickerNumber: string | null;
  location: Location;
  status: PrinterStatus;
  remarks: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface PrinterInput {
  brand: string;
  model: string;
  serialNumber: string | null;
  stickerNumber: string | null;
  locationId: number;
  status: PrinterStatus;
  remarks: string | null;
  version?: number;
}
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface ApiProblem {
  message: string;
  fieldErrors: Record<string, string>;
}

export interface RelocationInput {
  newLocationId: number;
  relocationDate: string;
  remarks: string | null;
  version: number;
}

export interface Relocation {
  id: number;
  printerId: number;
  previousLocation: Location;
  newLocation: Location;
  relocationDate: string;
  remarks: string | null;
  createdAt: string;
}

export interface Dashboard {
  totalPrinters: number;
  statusCounts: Record<PrinterStatus, number>;
  relocatedPrinters: number;
  recentlyAdded: Printer[];
  recentTransfers: { printer: Printer; relocation: Relocation }[];
  checkedAt: string;
}
