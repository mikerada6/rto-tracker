export type CommuteAnnotationCategory = 'SOCIAL' | 'ERRAND' | 'DINNER' | 'PERSONAL' | 'OTHER';

export interface CommuteAnnotation {
  id: string;
  startTime: string;
  endTime: string;
  category: CommuteAnnotationCategory;
  note: string | null;
}

export interface CreateCommuteAnnotationRequest {
  startTime: string;
  endTime: string;
  category: CommuteAnnotationCategory;
  note?: string | null;
}

export interface UpdateCommuteAnnotationRequest {
  category: CommuteAnnotationCategory;
  note?: string | null;
}

export const COMMUTE_ANNOTATION_CATEGORIES: {
  value: CommuteAnnotationCategory;
  label: string;
  icon: string;
}[] = [
  { value: 'SOCIAL', label: 'Social', icon: '🍻' },
  { value: 'ERRAND', label: 'Errand', icon: '🛒' },
  { value: 'DINNER', label: 'Dinner', icon: '🍽️' },
  { value: 'PERSONAL', label: 'Personal', icon: '🧘' },
  { value: 'OTHER', label: 'Other', icon: '🏷️' },
];

export function categoryMeta(category: CommuteAnnotationCategory) {
  return COMMUTE_ANNOTATION_CATEGORIES.find(c => c.value === category)
      ?? { value: category, label: category, icon: '🏷️' };
}
