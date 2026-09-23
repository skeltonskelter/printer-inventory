import axios from "axios";
import { api } from "./client";
import type {
  ApiProblem,
  Dashboard,
  Location,
  Page,
  Printer,
  PrinterInput,
  PrinterImportPreview,
  PrinterImportResult,
  Relocation,
  RelocationInput,
} from "../types/inventory";

export const inventory = {
  dashboard: async (signal?: AbortSignal) =>
    (await api.get<Dashboard>("/dashboard", { signal })).data,
  list: async (params: URLSearchParams, signal?: AbortSignal) =>
    (await api.get<Page<Printer>>("/printers", { params, signal })).data,
  previewImport: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return (await api.post<PrinterImportPreview>("/printers/import/preview", form)).data;
  },
  confirmImport: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return (await api.post<PrinterImportResult>("/printers/import/confirm", form)).data;
  },
  get: async (id: string, signal?: AbortSignal) =>
    (await api.get<Printer>(`/printers/${encodeURIComponent(id)}`, { signal }))
      .data,
  create: async (input: PrinterInput) =>
    (await api.post<Printer>("/printers", input)).data,
  update: async (id: number, input: PrinterInput) =>
    (await api.put<Printer>(`/printers/${id}`, input)).data,
  remove: async (id: number) => {
    await api.delete(`/printers/${id}`);
  },
  locations: async (signal?: AbortSignal) =>
    (await api.get<Location[]>("/locations", { signal })).data,
  createLocation: async (input: Record<string, string>) =>
    (await api.post<Location>("/locations", input)).data,
  updateLocation: async (
    id: number,
    input: Record<string, string | number>,
  ) => (await api.put<Location>(`/locations/${id}`, input)).data,
  relocate: async (id: number, input: RelocationInput) =>
    (await api.post<Printer>(`/printers/${id}/relocate`, input)).data,
  relocations: async (id: number, signal?: AbortSignal) =>
    (await api.get<Relocation[]>(`/printers/${id}/relocations`, { signal }))
      .data,
};

export function apiProblem(error: unknown): ApiProblem {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data;
    if (body && typeof body.message === "string")
      return { message: body.message, fieldErrors: body.fieldErrors ?? {} };
    if (!error.response)
      return {
        message:
          "The server could not be reached. Check your connection and try again.",
        fieldErrors: {},
      };
  }
  return {
    message: "The request could not be completed. Please try again.",
    fieldErrors: {},
  };
}
