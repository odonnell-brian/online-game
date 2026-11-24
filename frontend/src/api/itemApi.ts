import type { Item, NewItem } from "../types/itemTypes";
import { request } from "./http";

// Thin API client: keep all fetch calls here so components stay clean.
const listItems = () => request<Item[]>("/api/items");
const createItem = (item: NewItem) =>
  request<Item>("/api/items", { method: "POST", body: JSON.stringify(item) });

export const itemApi = {
  listItems,
  createItem,
};
