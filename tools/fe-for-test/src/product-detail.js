const reasonPresentations = {
  SAFE: { category: 'safe', iconUrl: '/assets/reason-safe.svg' },
  BENEFICIAL: { category: 'beneficial', iconUrl: '/assets/reason-beneficial.svg' },
  CAUTION: { category: 'caution', iconUrl: '/assets/reason-caution.svg' },
  WARNING: { category: 'warning', iconUrl: '/assets/reason-warning.svg' },
};

export function reasonPresentation(reason = {}) {
  const category = String(reason.assessmentCategory || 'CAUTION').toUpperCase();
  return reasonPresentations[category] || reasonPresentations.CAUTION;
}
