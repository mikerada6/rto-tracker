import { PeriodStats } from '../models/dashboard.model';

export type ComplianceStatus = 'on-track' | 'behind' | 'critical' | 'compliant' | 'non-compliant';

export function getComplianceStatus(period: PeriodStats, requiredAvg: number): ComplianceStatus {
  if (period.weeksRemaining <= 0) {
    return period.isCompliant ? 'compliant' : 'non-compliant';
  }
  if (period.requiredAvgForRemainder === null || period.requiredAvgForRemainder <= requiredAvg) {
    return 'on-track';
  }
  if (period.requiredAvgForRemainder > 5.0) {
    return 'critical';
  }
  return 'behind';
}

export function getStatusColor(status: ComplianceStatus): string {
  switch (status) {
    case 'on-track':
    case 'compliant':
      return 'var(--color-on-track)';
    case 'behind':
      return 'var(--color-behind)';
    case 'critical':
    case 'non-compliant':
      return 'var(--color-critical)';
  }
}

export function getStatusLabel(status: ComplianceStatus): string {
  switch (status) {
    case 'on-track':
      return 'On Track';
    case 'behind':
      return 'Behind';
    case 'critical':
      return 'At Risk';
    case 'compliant':
      return 'Compliant';
    case 'non-compliant':
      return 'Non-Compliant';
  }
}
