import type { Location } from "../types/inventory";

export function locationLabel(location: Location) {
  return [
    location.department,
    location.section,
    location.building,
    location.floor && `Floor ${location.floor}`,
    location.room && `Room ${location.room}`,
  ]
    .filter(Boolean)
    .join(" · ");
}
export function displayDate(value: string) {
  return new Date(value).toLocaleString();
}
