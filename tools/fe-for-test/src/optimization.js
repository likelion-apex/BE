export function optimizationPresentation(step = {}) {
  const rawStatus = String(step.status || 'VIDEO_PRODUCT').toLowerCase();
  const isVideoProduct = rawStatus === 'video_product' || rawStatus === 'no_inventory_match';
  const label = isVideoProduct ? '영상 속 제품' : '대체';
  return {
    status: isVideoProduct ? 'video-product' : 'replaced',
    label,
    reasonTitle: isVideoProduct ? '대체품 없음' : 'AI 대체 이유',
    sourceLabel: step.replaceName ? `영상 속 루틴: ${step.replaceName}` : label,
  };
}

export function optimizationScorePresentation(result = {}) {
  const rawScore = Number(result.overallScore);
  const score = Number.isFinite(rawScore) ? Math.max(0, Math.min(100, Math.round(rawScore))) : 0;
  const highlights = Array.isArray(result.highlights)
    ? result.highlights.filter((item) => typeof item === 'string' && item.trim()).slice(0, 2)
    : [];
  return { score, highlights };
}
