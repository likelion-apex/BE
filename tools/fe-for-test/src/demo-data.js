export const demoMember = {
  id: 1,
  email: 'soak@example.com',
  nickname: '윤지',
  profileImageUrl: '',
  provider: 'KAKAO',
  role: 'USER',
  skinType: '수부지',
  skinConcerns: ['속건조', '민감성'],
};

export const demoProducts = [
  {
    inventoryId: 101,
    productId: 1,
    productName: '어성초 진정 패드',
    brand: '아누아',
    category: 'SKIN_TONER',
    imageUrl: '/assets/product-jar.png',
    isFavorite: true,
  },
  {
    inventoryId: 102,
    productId: 2,
    productName: '비타민 C 앰플',
    brand: '구달',
    category: 'ESSENCE_SERUM_AMPOULE',
    imageUrl: '/assets/product-jar.png',
    isFavorite: true,
  },
  {
    inventoryId: 103,
    productId: 3,
    productName: '시카 장벽 크림',
    brand: '에스트라',
    category: 'CREAM',
    imageUrl: '/assets/product-jar.png',
    isFavorite: true,
  },
  {
    inventoryId: 104,
    productId: 4,
    productName: '셀퓨전씨 쿨링 패드',
    brand: '셀퓨전씨',
    category: 'SKIN_TONER',
    imageUrl: '',
    isFavorite: false,
  },
  {
    inventoryId: 105,
    productId: 5,
    productName: '메디힐 수분 앰플',
    brand: '메디힐',
    category: 'ESSENCE_SERUM_AMPOULE',
    imageUrl: '',
    isFavorite: false,
  },
];

export const demoHome = {
  todayCondition: {
    logged: true,
    condition: '촉촉하고편안해요',
    memo: '속당김 없이 촉촉했다!',
  },
  todayRoutine: {
    routineId: 21,
    name: '오늘의 나이트 케어',
    routineType: 'NIGHT',
    steps: [
      { order: 1, productId: 1, inventoryId: 101, productName: '어성초 진정 패드', category: '결 정돈 및 진정', imageUrl: '/assets/product-jar.png' },
      { order: 2, productId: 2, inventoryId: 102, productName: '비타민 C 항산화 앰플', category: '미백 및 안티에이징', imageUrl: '/assets/product-jar.png' },
      { order: 3, productId: 3, inventoryId: 103, productName: '세라마이드 캡슐 크림', category: '장벽 보호 및 보습', imageUrl: '/assets/product-jar.png' },
      { order: 4, productId: 4, inventoryId: 104, productName: '아이 링클 코어 크림', category: '눈가 주름 집중 케어', imageUrl: '/assets/product-jar.png' },
    ],
  },
  favoriteInventory: {
    totalFavoriteCount: 3,
    items: demoProducts.slice(0, 3),
  },
};

export const demoDailyRoutine = {
  ...demoHome.todayRoutine,
  completed: false,
  completionRate: 25,
  steps: demoHome.todayRoutine.steps.map((step, index) => ({
    ...step,
    stepId: 2101 + index,
    completed: index === 0,
  })),
};

export const demoRoutineLibrary = {
  totalCount: 2,
  routines: [
    { routineId: 71, name: '출근 전 수분 진정 루틴', routineType: 'DAY', stepCount: 3, createdAt: '2026-08-12T09:30:00' },
    { routineId: 72, name: '민감성 장벽 회복 루틴', routineType: 'NIGHT', stepCount: 4, createdAt: '2026-07-28T22:10:00' },
  ],
};

export const demoRoutineDetails = {
  71: {
    routineId: 71,
    name: '출근 전 수분 진정 루틴',
    routineType: 'DAY',
    status: 'ARCHIVED',
    steps: demoHome.todayRoutine.steps.slice(0, 3),
  },
  72: {
    routineId: 72,
    name: '민감성 장벽 회복 루틴',
    routineType: 'NIGHT',
    status: 'ARCHIVED',
    steps: demoHome.todayRoutine.steps,
  },
};

const demoNow = new Date();
const demoYear = demoNow.getFullYear();
const demoMonth = demoNow.getMonth() + 1;
const toDemoDate = (day) => `${demoYear}-${String(demoMonth).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
const demoTodayDay = demoNow.getDate();
const demoEarlierDay = Math.max(1, demoTodayDay - 3);

export const demoRoutineCalendar = {
  year: demoYear,
  month: demoMonth,
  completedDaysCount: 1,
  days: [
    { date: toDemoDate(demoEarlierDay), entries: [{ routineId: 72, routineType: 'NIGHT', completed: true }] },
    { date: toDemoDate(demoTodayDay), entries: [{ routineId: 21, routineType: 'NIGHT', completed: false }] },
  ],
};

export function createDemoRoutineDateDetail(date) {
  const isToday = date === toDemoDate(demoTodayDay);
  return {
    date,
    condition: isToday ? '촉촉하고 편안해요' : '평범하고 무난해요',
    memo: isToday ? '속당김 없이 촉촉했다!' : '자극 없이 무난한 하루',
    routineLogs: [{
      ...demoDailyRoutine,
      routineId: isToday ? 21 : 72,
      name: isToday ? '오늘의 나이트 케어' : '민감성 장벽 회복 루틴',
      completed: !isToday,
      completionRate: isToday ? demoDailyRoutine.completionRate : 100,
      steps: demoDailyRoutine.steps.map((step) => ({ ...step, completed: isToday ? step.completed : true })),
    }],
  };
}

export const demoGeneratedRoutine = {
  suggestedName: 'AI 추천 나이트 루틴',
  routineType: 'NIGHT',
  steps: [
    { order: 1, inventoryId: 101, category: 'SKIN_TONER', productName: '어성초 진정 패드', matchScore: 94 },
    { order: 2, inventoryId: 102, category: 'ESSENCE_SERUM_AMPOULE', productName: '비타민 C 앰플', matchScore: 88 },
    { order: 3, inventoryId: 103, category: 'CREAM', productName: '시카 장벽 크림', matchScore: 96 },
  ],
  warnings: ['비타민 C 앰플은 민감한 날 격일 사용을 권장해요.'],
};

export const demoHistory = [
  { analysisId: 301, status: 'COMPLETED', title: '속건조 타파 루틴', stepCount: 4, overallScore: 88 },
  { analysisId: 302, status: 'COMPLETED', title: '민감성 진정 3단계 시카 루틴', stepCount: 3, overallScore: 88 },
  { analysisId: 303, status: 'COMPLETED', title: '여드름 흉터 지우는 레티놀 조합', stepCount: 5, overallScore: 75 },
];

export const demoPreview = {
  thumbnailUrl: '/assets/analysis-face.png',
  title: '여름철 수부지를 위한 4단계 스킨케어',
  publisher: 'SOAK Beauty',
  viewCount: '12만회',
  duration: '0:42',
};

export const demoAnalysisResult = {
  title: '속건조 타파 루틴',
  tag: '여름철 수부지 맞춤',
  overallScore: 88,
  highlights: ['수부지 맞춤 보습 성분 12개 매칭', '알레르기 유발 성분 0개'],
  coreGoal: '속건조 해결 & 장벽 재생',
  synergyCombo: '히알루론산 + 고함량 판테놀',
  summary: '수분 공급과 장벽 회복을 함께 노린 루틴이에요. 자극 가능성이 있는 각질 제거 단계는 주 2~3회 사용을 권장합니다.',
  steps: [
    {
      resultId: 401,
      order: 1,
      category: '클렌징',
      displayProductName: '초미세먼지 세정 클렌저',
      productName: '초미세먼지 세정 클렌저',
      imageUrl: '/assets/product-jar.png',
      primaryAssessmentCategory: 'SAFE',
      safetySummary: '세정력이 뛰어나면서 자극이 적어 민감성 피부도 안심이에요.',
      benefitSummary: '뛰어난 세정력과 촉촉한 마무리감',
    },
    {
      resultId: 402,
      order: 2,
      category: '토너 (주의)',
      displayProductName: '라운드랩 1025 독도 토너',
      productName: '라운드랩 1025 독도 토너',
      imageUrl: '/assets/product-jar.png',
      primaryAssessmentCategory: 'WARNING',
      safetySummary: '민감성 피부에는 매일 사용 시 자극이 될 수 있어요. 주 2~3회만 사용하세요.',
      benefitSummary: '수분 공급 및 피지·각질 제거',
    },
    {
      resultId: 403,
      order: 3,
      category: '앰플',
      displayProductName: '라운드랩 자작나무 수분 앰플',
      productName: '라운드랩 자작나무 수분 앰플',
      imageUrl: '/assets/product-jar.png',
      primaryAssessmentCategory: 'BENEFICIAL',
      safetySummary: '자작나무 수액과 히알루론산이 수부지 피부에 잘 맞아요.',
      benefitSummary: '산뜻하고 쫀쫀한 속건조 케어',
    },
    {
      resultId: 404,
      order: 4,
      category: '크림',
      displayProductName: '고함량 판테놀 10% 재생 크림',
      productName: '고함량 판테놀 10% 재생 크림',
      imageUrl: '/assets/product-jar.png',
      primaryAssessmentCategory: 'BENEFICIAL',
      safetySummary: '판테놀이 약해진 민감성 피부 장벽을 튼튼하게 재생해 줘요.',
      benefitSummary: '피부 장벽 회복 및 재생',
    },
  ],
};

export const demoOptimization = {
  newProductCount: 1,
  compatibleCount: 2,
  replacedCount: 1,
  missingCount: 1,
  summary: '자극적인 성분은 빼고 역할이 같은 보유 제품으로 안전하게 재구성했어요.',
  steps: [
    { order: 1, productName: '초미세먼지 세정 클렌저', status: 'MISSING', reason: '대체 제품이 인벤토리에 없어요.' },
    { order: 2, productName: '셀퓨전씨 쿨링 패드', status: 'REPLACED', reason: '각질 제거 토너 대신 보유한 진정 패드로 교체했어요.' },
    { order: 3, productName: '메디힐 수분 앰플', status: 'COMPATIBLE', reason: '동일한 히알루론산 기반 보유 제품이에요.' },
    { order: 4, productName: '에스트라 아토베리어 로션', status: 'COMPATIBLE', reason: '손상된 장벽을 보호하는 보유 제품이에요.' },
  ],
};

export const demoProductDetail = {
  matchScore: 88,
  displayBrand: '라운드랩',
  displayProductName: '자작나무 수분 앰플',
  category: '앰플',
  imageUrl: '/assets/product-jar.png',
  ingredientDataStatus: 'AVAILABLE',
  reasons: [
    { title: '속보습 강화', description: '히알루론산이 피부 안쪽 수분 유지에 도움을 줘요.', assessmentCategory: 'BENEFICIAL' },
    { title: '민감 피부 적합', description: '주의 성분이 적어 현재 피부 프로필에 잘 맞아요.', assessmentCategory: 'SAFE' },
  ],
  ingredientStats: {
    totalCount: 18,
    caution20Count: 0,
    allergenCount: 0,
    lowRiskCount: 15,
    moderateRiskCount: 3,
    highRiskCount: 0,
    unknownRiskCount: 0,
  },
  ingredients: [
    { name: '정제수', riskScore: 1, riskLevel: 'LOW', purposes: ['용제'], skinBenefits: [] },
    { name: '자작나무수액', riskScore: 1, riskLevel: 'LOW', purposes: ['보습'], skinBenefits: ['수분 공급'] },
    { name: '히알루론산', riskScore: 1, riskLevel: 'LOW', purposes: ['보습제'], skinBenefits: ['속보습'] },
  ],
};
