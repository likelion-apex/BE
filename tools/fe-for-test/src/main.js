import './styles.css';
import { ApiClient } from './api.js';
import { optimizationPresentation, optimizationScorePresentation } from './optimization.js';
import { needsIngredientReanalysis, reasonPresentation } from './product-detail.js';
import {
  conditionOptions,
  conditionPresentation,
  formatRoutineRecordDate,
  routineCompletion,
  routineRecordView,
  routineTypeLabel,
  sortedRoutineSteps,
} from './routine-date-detail.js';
import {
  buildGeneratedRoutineCreatePayload,
  buildKakaoAuthorizeUrl,
  buildRoutineApplyPayload,
  buildRoutineLogsPath,
  createOAuthState,
  normalizeKakaoClientId,
} from './config.js';
import {
  createDemoRoutineDateDetail,
  demoAnalysisResult,
  demoDailyRoutine,
  demoGeneratedRoutine,
  demoHistory,
  demoHome,
  demoMember,
  demoOptimization,
  demoPreview,
  demoProductDetail,
  demoProducts,
  demoRoutineCalendar,
  demoRoutineDetails,
  demoRoutineLibrary,
} from './demo-data.js';

const env = (value, fallback = '') => String(value || fallback).trim();
const root = document.querySelector('#app');
const dialog = document.querySelector('#app-dialog');
const toastRegion = document.querySelector('#toast-region');

const rawClientId = env(import.meta.env.VITE_KAKAO_CLIENT_ID);
const kakaoConfig = normalizeKakaoClientId(rawClientId);
const defaultRedirectUri = `${window.location.origin}/onboarding/kakaocallback`;

const skinTypes = [
  { value: '건성', title: '건성', description: '세안 후 피부가 자주 당기고 건조해요', icon: 'skin-dry.png' },
  { value: '중성', title: '중성', description: '유수분 밸런스가 적당하고 편안해요', icon: 'skin-normal.png' },
  { value: '지성', title: '지성', description: '유분기가 많고 금방 번들거려요', icon: 'skin-oily.png' },
  { value: '복합성', title: '복합성', description: 'T존은 지성, U존은 건성이에요', icon: 'skin-combination.png' },
  { value: '수부지', title: '수부지 (수분 부족형 지성)', description: '겉은 번들거리는데 속은 찢어질 듯 당겨요', icon: 'skin-dehydrated.png' },
];

const concerns = ['속건조', '여드름', '민감성', '미백·잡티', '다크서클', '색소·블랙헤드', '홍조', '아토피'];

const categoryLabels = {
  CLEANSER: '클렌저',
  SKIN_TONER: '스킨/토너',
  LOTION_EMULSION: '로션/에멀전',
  ESSENCE_SERUM_AMPOULE: '에센스/앰플/세럼',
  CREAM: '크림',
  SUN_CARE: '선케어',
  MASK_PACK: '마스크/팩',
  EYE_CARE: '아이케어',
  ETC: '기타',
};

function createRoutineState() {
  const now = new Date();
  return {
    tab: 'today',
    daily: null,
    dailyLoaded: false,
    library: { totalCount: 0, routines: [] },
    libraryLoaded: false,
    year: null,
    sort: 'LATEST',
    calendar: null,
    calendarLoaded: false,
    calendarYear: now.getFullYear(),
    calendarMonth: now.getMonth() + 1,
    selectedDateDetail: null,
    generated: null,
    generateType: now.getHours() >= 6 && now.getHours() < 18 ? 'DAY' : 'NIGHT',
  };
}

const state = {
  backendUrl: env(import.meta.env.VITE_BACKEND_URL, 'https://mutsa.dev.me.kr').replace(/\/$/, ''),
  redirectUri: env(import.meta.env.VITE_KAKAO_REDIRECT_URI, defaultRedirectUri),
  accessToken: '',
  refreshToken: '',
  member: null,
  isDemo: false,
  loading: false,
  activeView: 'home',
  onboardingStep: 0,
  onboarding: { skinType: '건성', skinConcerns: ['속건조'], nickname: '' },
  home: null,
  routine: createRoutineState(),
  inventory: { totalCount: 0, items: [] },
  inventoryTab: 'all',
  inventorySearch: '',
  searchResults: [],
  history: [],
  videoUrl: 'https://www.youtube.com/shorts/t1S24pgO2XQ',
  preview: null,
  previewLoading: false,
  analysisId: null,
  analysisStatus: null,
  analysisResult: null,
  optimization: null,
  savedRoutine: null,
  debug: null,
  error: '',
};

const api = new ApiClient({
  baseUrl: state.backendUrl,
  getAccessToken: () => state.accessToken,
});

const escapeHtml = (value) => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#039;');

const safeImageUrl = (value, fallback = '/assets/product-placeholder.png') => {
  const raw = String(value || '').trim();
  if (raw.startsWith('/')) return raw;
  try {
    const parsed = new URL(raw);
    return ['http:', 'https:'].includes(parsed.protocol) ? parsed.toString() : fallback;
  } catch {
    return fallback;
  }
};

const icon = (name, label = '') => `<img class="ui-icon" src="/assets/${name}" alt="${escapeHtml(label)}">`;

function toast(message, type = 'info') {
  const item = document.createElement('div');
  item.className = `toast ${type}`;
  item.textContent = message;
  toastRegion.append(item);
  requestAnimationFrame(() => item.classList.add('visible'));
  window.setTimeout(() => {
    item.classList.remove('visible');
    window.setTimeout(() => item.remove(), 180);
  }, 3200);
}

function setBusy(isBusy) {
  state.loading = isBusy;
  document.body.classList.toggle('is-busy', isBusy);
}

function statusBar() {
  return `<div class="status-bar" aria-hidden="true"><b>9:41</b><span class="status-notch"></span><span>▥ ◔ ▰</span></div>`;
}

function bottomNav(active = state.activeView) {
  const items = [
    ['home', 'nav-home.svg', '홈'],
    ['analysis', 'nav-analysis.svg', 'AI분석'],
    ['routine', 'nav-routine.svg', '루틴'],
    ['inventory', 'nav-inventory.svg', '인벤토리'],
    ['profile', 'nav-profile.svg', 'My'],
  ];
  return `<nav class="bottom-nav" aria-label="주 메뉴">
    ${items.map(([view, image, label]) => `
      <button class="nav-item ${view === active ? 'active' : ''}" type="button" data-view="${view}">
        ${icon(image)}<span>${label}</span>
      </button>`).join('')}
  </nav>`;
}

function screen(content, { active = state.activeView, className = '', noNav = false } = {}) {
  return `<main class="phone-screen ${className}">
    ${statusBar()}
    <div class="screen-scroll">${content}</div>
    ${noNav ? '' : bottomNav(active)}
  </main>`;
}

function pageHeader(title, { back = false, action = '' } = {}) {
  return `<header class="page-header">
    ${back ? `<button class="icon-button" type="button" data-action="back" aria-label="뒤로">${icon('back.svg')}</button>` : '<span class="header-spacer"></span>'}
    <strong>${escapeHtml(title)}</strong>
    ${action || '<span class="header-spacer"></span>'}
  </header>`;
}

function render() {
  if (!state.accessToken && !state.isDemo && state.activeView !== 'splash') {
    state.activeView = 'splash';
  }

  if (state.activeView === 'splash') root.innerHTML = renderSplash();
  else if (state.activeView === 'onboarding') root.innerHTML = renderOnboarding();
  else if (state.activeView === 'home') root.innerHTML = renderHome();
  else if (state.activeView === 'analysis') root.innerHTML = renderAnalysis();
  else if (state.activeView === 'routine') root.innerHTML = renderRoutine();
  else if (state.activeView === 'inventory') root.innerHTML = renderInventory();
  else if (state.activeView === 'profile') root.innerHTML = renderProfile();
  else root.innerHTML = renderSplash();
}

function renderSplash() {
  const configWarning = !kakaoConfig.clientId
    ? '<p class="splash-warning">카카오 로그인 키가 아직 설정되지 않았어요. UI 미리보기 또는 테스트 토큰으로 먼저 확인할 수 있어요.</p>'
    : kakaoConfig.formatIsValid
      ? ''
      : '<p class="splash-warning error">VITE_KAKAO_CLIENT_ID 형식을 확인해 주세요.</p>';
  return screen(`
    <section class="splash-content">
      <div class="brand-lockup">
        <img class="splash-logo" src="/assets/soak-logo.png" alt="SOAK">
        <img class="soak-wordmark" src="/assets/soak-wordmark.png" alt="SOAK">
      </div>
      <h1>나만을 위한 맞춤형 AI<br>스킨 케어 서비스</h1>
      ${configWarning}
      <div class="splash-actions">
        <button class="kakao-button" type="button" data-action="kakao-login">
          <span class="kakao-icon"><img src="/assets/kakao-login.png" alt=""></span>
          <strong>카카오로 시작하기</strong><span></span>
        </button>
        <button class="ghost-on-blue" type="button" data-action="demo-login">UI 미리보기</button>
        <button class="text-on-blue" type="button" data-action="open-settings">테스트 토큰으로 연결</button>
      </div>
    </section>
  `, { noNav: true, className: 'splash-screen' });
}

function renderOnboarding() {
  const progress = [50, 75, 100][state.onboardingStep] || 50;
  const commonTop = `
    <div class="onboarding-top">
      <button class="icon-button" type="button" data-action="onboarding-back" aria-label="뒤로">${icon('back.svg')}</button>
      <div class="progress-track"><span style="width:${progress}%"></span></div>
    </div>`;

  if (state.onboardingStep === 0) {
    return screen(`${commonTop}
      <section class="onboarding-body">
        <div class="intro-copy"><h1>피부 타입을 선택해주세요</h1><p>AI가 내 피부에 꼭 맞는 관리를 도와드릴게요.</p></div>
        <div><span class="field-hint">1개만 선택해주세요</span>
          <div class="skin-type-list">
            ${skinTypes.map((item) => {
              const selected = state.onboarding.skinType === item.value;
              return `<button class="skin-type ${selected ? 'selected' : ''}" type="button" data-skin-type="${item.value}">
                <img src="/assets/${item.icon}" alt=""><span><b>${item.title}</b><small>${item.description}</small></span>
                ${selected ? '<i class="selection-check">✓</i>' : ''}
              </button>`;
            }).join('')}
          </div>
        </div>
      </section>
      <button class="bottom-cta" type="button" data-action="onboarding-next">다음</button>
    `, { noNav: true, className: 'onboarding-screen' });
  }

  if (state.onboardingStep === 1) {
    return screen(`${commonTop}
      <section class="onboarding-body">
        <div class="intro-copy"><h1>어떤 피부 고민이 있으신가요?</h1><p>고민을 알려주시면 AI가 가장 효과적인<br>해결책을 찾아드려요.</p></div>
        <div><span class="field-hint">중복 선택 가능</span>
          <div class="concern-cloud">
            ${concerns.map((item, index) => `<button type="button" class="concern-chip ${state.onboarding.skinConcerns.includes(item) ? 'selected' : ''}" data-concern="${item}">
              <span class="concern-mark">${index + 1}</span>${item}
            </button>`).join('')}
          </div>
        </div>
      </section>
      <button class="bottom-cta" type="button" data-action="onboarding-next">다음</button>
    `, { noNav: true, className: 'onboarding-screen' });
  }

  const nickname = state.onboarding.nickname;
  return screen(`${commonTop}
    <section class="onboarding-body">
      <div class="intro-copy"><h1>SOAK에서 사용할<br>닉네임을 입력해주세요</h1><p>나중에 마이페이지에서 변경할 수 있어요.</p></div>
      <label class="nickname-field"><input id="onboarding-nickname" maxlength="10" value="${escapeHtml(nickname)}" placeholder="닉네임 입력 (최대 10자)"><button type="button" data-action="clear-nickname" aria-label="지우기">×</button></label>
      <span class="character-count">${[...nickname].length}/10</span>
    </section>
    <button class="bottom-cta" type="button" data-action="complete-onboarding" ${nickname.trim() ? '' : 'disabled'}>완료</button>
  `, { noNav: true, className: 'onboarding-screen' });
}

function renderHome() {
  const home = state.home;
  const nickname = state.member?.nickname || '사용자';
  const condition = home?.todayCondition || {};
  const todayRoutine = home?.todayRoutine;
  const favorites = home?.favoriteInventory?.items || [];
  return screen(`
    <header class="brand-header">
      <span class="mini-brand"><img src="/assets/soak-logo.png" alt=""><img src="/assets/soak-wordmark.png" alt="SOAK"></span>
      <button class="icon-button" type="button" data-action="open-settings" aria-label="테스트 설정">${icon('menu.svg')}</button>
    </header>
    <section class="page-section home-greeting">
      <h1>오늘 하루도 고생 많았어요.</h1>
      <p>${escapeHtml(nickname)} 님, 지금 피부 컨디션은 어떤가요?</p>
    </section>
    <section class="condition-card">
      <div class="condition-options">
        ${conditionOptions.map((item) => {
          const selected = condition.logged && condition.condition === item.value;
          return `<button class="condition-option ${selected ? 'selected' : ''}" type="button" data-condition="${item.value}">
            <span><img src="/assets/${item.icon}" alt=""></span><small>${escapeHtml(item.short).replace('\n', '<br>')}</small>
          </button>`;
        }).join('')}
      </div>
      <form class="condition-note" id="condition-form">
        <input name="memo" maxlength="30" value="${escapeHtml(condition.memo || '')}" placeholder="오늘 피부 상태를 한 줄로 기록해보세요">
        <button type="submit">${condition.logged ? '수정' : '작성'}</button>
      </form>
    </section>
    <section class="page-section">
      <div class="section-heading"><h2>${todayRoutine?.routineType === 'DAY' ? '오늘의 데이 케어' : '오늘의 나이트 케어'}</h2><button type="button" data-view="routine">더보기 ›</button></div>
      ${todayRoutine ? renderTodayRoutine(todayRoutine) : `<div class="empty-card"><b>아직 오늘의 루틴이 없어요</b><p>AI 분석으로 내 피부에 맞는 루틴을 만들어 보세요.</p><button class="small-primary" type="button" data-view="analysis">루틴 분석하기</button></div>`}
    </section>
    <section class="page-section">
      <h2>AI 루틴분석</h2>
      <div class="ai-quick-card">
        <img src="/assets/ai-orb.png" alt="">
        <h3>AI 분석 요청하기</h3>
        <p>링크를 붙여넣으세요. 내 피부에 필요한 제품인지, 인벤토리 제품으로 대신할 수 있는지 AI가 분석해 드릴게요.</p>
        <button class="primary-button" type="button" data-view="analysis">AI 분석 요청하기</button>
      </div>
    </section>
    <section class="page-section product-section">
      <div class="section-heading"><h2>즐겨찾는 화장품</h2><button type="button" data-action="show-favorites">전체보기 ›</button></div>
      ${renderProductRail(favorites)}
    </section>
  `, { active: 'home' });
}

function renderTodayRoutine(routine) {
  return `<article class="routine-card">
    <div class="routine-callout">피부 컨디션에 맞춘 오늘의 추천 순서예요.</div>
    <div class="routine-step-list">
      ${(routine.steps || []).map((step) => `<button type="button" class="routine-row" data-product-id="${step.productId || ''}">
        <img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt="">
        <span><b>${step.order}. ${escapeHtml(step.productName)}</b><small>${escapeHtml(step.category || step.brand || '')}</small></span>
      </button>`).join('')}
    </div>
  </article>`;
}

function renderProductRail(items) {
  if (!items.length) return '<div class="empty-inline">즐겨찾는 제품이 아직 없어요.</div>';
  return `<div class="product-rail">${items.map((item) => `<button class="product-tile" type="button" data-inventory-id="${item.inventoryId || ''}" data-product-id="${item.productId || ''}">
    <img src="${safeImageUrl(item.imageUrl, '/assets/product-jar.png')}" alt="">
    <b>${escapeHtml(item.productName)}</b><small>${escapeHtml(categoryLabels[item.category] || item.category || item.brand || '')}</small>
  </button>`).join('')}</div>`;
}

function renderAnalysis() {
  if (state.analysisStatus && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(state.analysisStatus.status)) {
    return renderAnalysisProgress();
  }
  if (state.analysisResult) return renderAnalysisResult();

  const recent = state.history || [];
  return screen(`
    ${pageHeader('AI 루틴 분석', { back: false })}
    <section class="analysis-hero page-section">
      <h1>영상 속 스킨케어 루틴,<br>내 피부에도 잘 맞을까요?</h1>
      <p>유튜브 쇼츠 링크를 붙여넣어 보세요.</p>
      <div class="analysis-visuals" aria-hidden="true">
        <img src="/assets/analysis-bottles.png" alt=""><img src="/assets/analysis-face.png" alt=""><img src="/assets/analysis-cream.png" alt="">
      </div>
    </section>
    <section class="analysis-input-card page-section">
      <form id="analysis-form">
        <div class="url-field"><input id="video-url" name="videoUrl" value="${escapeHtml(state.videoUrl)}" placeholder="영상의 URL을 입력하세요"><button type="button" data-action="paste-url">붙여넣기</button></div>
        ${state.previewLoading ? '<div class="inline-loading"><span></span> 영상 정보를 확인하고 있어요</div>' : ''}
        ${state.preview ? renderPreview(state.preview) : ''}
        <button class="primary-button" type="submit" ${state.preview || state.isDemo ? '' : 'disabled'}>AI 분석 요청하기</button>
      </form>
    </section>
    <p class="analysis-notice">최대 5분 이내 영상만 분석할 수 있어요.<br>영상 길이에 따라 분석에 다소 시간이 걸릴 수 있어요.</p>
    <section class="page-section process-section">
      <h2>AI 분석, 이렇게 진행돼요</h2>
      <div class="process-card">
        <div><span>${icon('process-link.svg')}</span><b>링크 입력</b></div><i>→</i>
        <div><span>${icon('process-product.svg')}</span><b>제품 추출</b></div><i>→</i>
        <div><span>${icon('process-match.svg')}</span><b>맞춤 큐레이션</b></div>
      </div>
    </section>
    <section class="page-section recent-section">
      <div class="section-heading"><h2>최근 분석한 루틴</h2><button type="button" data-action="reload-history">새로고침</button></div>
      <div class="recent-list">
        ${recent.length ? recent.map((item) => `<button type="button" class="recent-item" data-analysis-id="${item.analysisId}" data-analysis-status="${item.status}">
          <img class="recent-thumb" src="${safeImageUrl(item.thumbnailUrl, '/assets/analysis-face.png')}" alt="" loading="lazy"><span><b>${escapeHtml(item.title || '분석 중인 루틴')}</b><small>${item.stepCount || 0}단계 · ${escapeHtml(item.status)}</small></span><strong>${item.overallScore == null ? '›' : `${item.overallScore}점`}</strong>
        </button>`).join('') : '<div class="empty-inline">아직 분석한 루틴이 없어요.</div>'}
      </div>
    </section>
  `, { active: 'analysis' });
}

function renderPreview(preview) {
  return `<article class="video-preview">
    <img src="${safeImageUrl(preview.thumbnailUrl, '/assets/analysis-face.png')}" alt="영상 썸네일">
    <span><b>${escapeHtml(preview.title || '제목 없는 영상')}</b><small>${escapeHtml(preview.publisher || '게시자 미확인')} · ${escapeHtml(preview.duration || '-')} · ${escapeHtml(preview.viewCount || '조회수 비공개')}</small></span>
  </article>`;
}

function renderAnalysisProgress() {
  const status = state.analysisStatus || { status: 'PENDING', progress: 0, message: '분석을 준비하고 있어요.' };
  const phases = [
    ['EXTRACTING_VIDEO', '영상에서 제품을 찾고 있어요'],
    ['MATCHING_PRODUCTS', '제품과 성분 정보를 확인하고 있어요'],
    ['PERSONALIZING', '피부 프로필에 맞춰 분석하고 있어요'],
    ['OPTIMIZING', '인벤토리와 안전한 조합을 만들고 있어요'],
  ];
  const currentIndex = phases.findIndex(([value]) => value === status.status);
  return screen(`
    ${pageHeader('AI 루틴 분석', { back: true })}
    <section class="loading-screen-content">
      <div class="smart-loader"><img src="/assets/ai-orb.png" alt=""><span></span></div>
      <span class="eyebrow">SOAK AI가 분석 중이에요</span>
      <h1>${escapeHtml(status.message || '영상 속 루틴을 꼼꼼하게 살펴보고 있어요')}</h1>
      <p>화면을 닫아도 서버에서는 분석이 계속됩니다.</p>
      <div class="large-progress"><span style="width:${Math.max(4, Number(status.progress || 0))}%"></span></div>
      <b class="progress-number">${Number(status.progress || 0)}%</b>
      <div class="phase-list">
        ${phases.map(([value, label], index) => `<div class="phase ${index < currentIndex ? 'done' : ''} ${value === status.status ? 'active' : ''}"><i>${index < currentIndex ? '✓' : index + 1}</i><span>${label}</span></div>`).join('')}
      </div>
      <button class="secondary-button" type="button" data-action="cancel-analysis">분석 취소</button>
    </section>
  `, { noNav: true, className: 'analysis-progress-screen' });
}

function renderAnalysisResult() {
  const result = state.analysisResult;
  const nickname = state.member?.nickname || '사용자';
  return screen(`
    ${pageHeader('AI 분석 결과', { back: true })}
    <div class="result-progress"><span style="width:${state.optimization ? '72%' : '50%'}"></span></div>
    <section class="result-intro page-section">
      <h1>${escapeHtml(nickname)}님이 공유하신 영상에서<br>핵심 루틴만 AI가 쏙 뽑아왔어요</h1>
      <p>단계별 성분이 내 피부에 맞는지 미리 체크해 보세요.</p>
    </section>
    ${state.optimization ? renderOptimization(state.optimization) : renderBriefing(result)}
    <section class="page-section result-steps-section">
      <h2>${state.optimization ? '오늘을 위한 안전한 맞춤 루틴' : '영상 속 루틴 성분 분석'}</h2>
      <div class="result-step-grid ${state.optimization ? 'optimized' : ''}">
        ${state.optimization ? renderOptimizedSteps(state.optimization.steps) : renderResultSteps(result.steps)}
      </div>
    </section>
    <section class="result-action page-section">
      ${state.optimization
        ? `<p>이 안전한 루틴으로 오늘 케어할까요?</p><button class="primary-button" type="button" data-action="open-save-routine">이 안전한 루틴으로 오늘 케어하기</button>`
        : `<p>이제 ${escapeHtml(nickname)}님의 인벤토리와 성분 충돌이 없는지 알아볼까요?</p><button class="primary-button" type="button" data-action="optimize-analysis">인벤토리 제품과 성분 궁합 확인하기</button>`}
    </section>
  `, { active: 'analysis' });
}

function renderBriefing(result) {
  return `<section class="briefing-card page-section">
    <div class="briefing-title"><b>${escapeHtml(result.title || 'AI 맞춤 루틴')}</b><span>${escapeHtml(result.tag || '맞춤 분석')}</span></div>
    <div class="score-panel"><strong>AI 매칭 점수 ${Number(result.overallScore || 0)}점</strong>${(result.highlights || []).map((item) => `<small>☑ ${escapeHtml(item)}</small>`).join('')}</div>
    <dl><div><dt>루틴 핵심 목표</dt><dd>${escapeHtml(result.coreGoal || '-')}</dd></div><div><dt>시너지 성분 조합</dt><dd>${escapeHtml(result.synergyCombo || '-')}</dd></div></dl>
    <p>${escapeHtml(result.summary || '')}</p>
  </section>`;
}

function renderResultSteps(steps = []) {
  return steps.map((step) => {
    const category = String(step.primaryAssessmentCategory || 'CAUTION').toLowerCase();
    return `<button type="button" class="result-step" data-result-id="${step.resultId || ''}">
      <span class="step-label"><i>${step.order}</i>${escapeHtml(step.category)}</span>
      <img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt="">
      <b>${escapeHtml(step.displayProductName || step.productName)}</b>
      <small>${escapeHtml(step.benefitSummary || step.benefit || '')}</small>
      <span class="assessment ${category}"><strong>${assessmentLabel(category)}</strong>${escapeHtml(step.safetySummary || '')}</span>
    </button>`;
  }).join('');
}

function assessmentLabel(category) {
  return ({ safe: '피부 안전도 평가', beneficial: '내 피부에 좋은 성분', caution: '사용 시 주의', warning: 'AI 경고' })[category] || 'AI 분석';
}

function renderOptimization(result) {
  const score = optimizationScorePresentation(result);
  return `<section class="optimization-report page-section">
    <span class="report-badge">AI 매칭 리포트</span>
    <div class="score-panel"><strong>AI 매칭 점수 ${score.score}점</strong>${score.highlights.map((item) => `<small>☑ ${escapeHtml(item)}</small>`).join('')}</div>
    <dl>
      <div><dt>숏폼 속 새로운 제품</dt><dd>${Number(result.newProductCount || 0)}개</dd></div>
      <div><dt>AI가 안전하게 대체한 제품</dt><dd class="blue">${Number(result.replacedCount || 0)}개</dd></div>
    </dl>
    <p>${escapeHtml(result.summary || '')}</p>
  </section>`;
}

function renderOptimizedSteps(steps = []) {
  return steps.map((step) => {
    const { status, label, reasonTitle, sourceLabel } = optimizationPresentation(step);
    return `<article class="optimized-step">
      <div class="optimized-head"><i>${step.order}</i><img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt=""><span><b>${escapeHtml(step.productName)}</b><small>${escapeHtml(sourceLabel)}</small></span><em class="status-${status}">${escapeHtml(label)}</em></div>
      <div class="optimization-reason ${status}"><b>${escapeHtml(reasonTitle)}</b><p>${escapeHtml(step.reason || '')}</p></div>
    </article>`;
  }).join('');
}

function renderRoutine() {
  const tabs = [
    ['today', '오늘'],
    ['library', '보관함'],
    ['calendar', '캘린더'],
    ['generate', 'AI 생성'],
  ];
  return screen(`
    ${pageHeader('루틴', { action: '<button class="header-action" type="button" data-action="reload-routine">새로고침</button>' })}
    <nav class="routine-tabs" aria-label="루틴 메뉴">
      ${tabs.map(([tab, label]) => `<button class="${state.routine.tab === tab ? 'active' : ''}" type="button" data-routine-tab="${tab}">${label}</button>`).join('')}
    </nav>
    <div class="routine-tab-content">
      ${state.routine.tab === 'today' ? renderDailyRoutine() : ''}
      ${state.routine.tab === 'library' ? renderRoutineLibrary() : ''}
      ${state.routine.tab === 'calendar' ? renderRoutineCalendar() : ''}
      ${state.routine.tab === 'generate' ? renderRoutineGenerator() : ''}
    </div>
  `, { active: 'routine' });
}

function renderDailyRoutine() {
  if (!state.routine.dailyLoaded) return '<section class="page-section routine-loading"><span></span>오늘의 루틴을 불러오고 있어요</section>';
  const routine = state.routine.daily;
  if (!routine) {
    return `<section class="page-section routine-empty-state"><img src="/assets/ai-orb.png" alt=""><h1>아직 오늘의 루틴이 없어요</h1><p>영상 분석 또는 AI 자동생성으로 내 피부에 맞는 루틴을 시작해 보세요.</p><button class="primary-button" type="button" data-routine-tab="generate">AI 루틴 만들기</button><button class="secondary-button" type="button" data-view="analysis">영상 루틴 분석하기</button></section>`;
  }
  const rate = Math.max(0, Math.min(100, Number(routine.completionRate || 0)));
  const allStepsCompleted = rate === 100;
  return `
    <section class="routine-summary-hero page-section ${routine.completed ? 'completed' : ''}">
      <div class="routine-hero-top"><span class="eyebrow">${routine.completed ? '오늘의 케어를 완료했어요' : '피부를 위한 오늘의 순서예요'}</span><strong>${rate}%</strong></div>
      <h1>${escapeHtml(routine.name)}</h1>
      <p>${routine.steps?.length || 0}단계 · ${routine.routineType === 'DAY' ? '데이 케어' : '나이트 케어'}</p>
      <div class="routine-progress" aria-label="루틴 달성률 ${rate}%"><span style="width:${rate}%"></span></div>
    </section>
    ${routine.aiBriefing ? renderBriefing(routine.aiBriefing) : ''}
    <section class="page-section daily-routine-section">
      <div class="section-heading"><h2>오늘의 케어 순서</h2><span>단계를 눌러 완료 체크</span></div>
      <div class="daily-routine-list">
        ${(routine.steps || []).map((step) => `<button class="daily-routine-step ${step.completed ? 'completed' : ''}" type="button" data-action="toggle-routine-step" data-step-id="${step.stepId}" data-completed="${step.completed}">
          <i>${step.completed ? '✓' : step.order}</i><img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt=""><span><b>${escapeHtml(step.productName)}</b><small>${escapeHtml(categoryLabels[step.category] || step.category || step.brand || '')}</small></span><em>${step.completed ? '완료' : '체크'}</em>
        </button>`).join('')}
      </div>
      <div class="routine-action-grid">
        <button class="secondary-button" type="button" data-action="complete-all-routine-steps" ${allStepsCompleted || routine.completed ? 'disabled' : ''}>모든 단계 체크</button>
        <button class="primary-button" type="button" data-action="complete-today-routine" ${!allStepsCompleted || routine.completed ? 'disabled' : ''}>${routine.completed ? '오늘 루틴 완료됨' : '오늘 루틴 완료'}</button>
      </div>
    </section>`;
}

function renderRoutineLibrary() {
  const library = state.routine.library;
  return `
    <section class="page-section routine-library-head">
      <div><span class="eyebrow">나의 루틴 보관함</span><h1>다시 쓰고 싶은 루틴을<br>오늘 케어로 불러오세요</h1></div>
      <div class="routine-filter-row">
        <select data-routine-year-select aria-label="연도 선택">
          <option value="" ${!state.routine.year ? 'selected' : ''}>전체(최근 3년)</option>
          <option value="2026" ${state.routine.year === '2026' ? 'selected' : ''}>2026년</option>
          <option value="2025" ${state.routine.year === '2025' ? 'selected' : ''}>2025년</option>
        </select>
        <div class="period-filter" aria-label="정렬">
          ${[['LATEST', '최신순'], ['NAME', '가나다순'], ['STEP_COUNT', '단계순'], ['SCORE', 'AI 매칭점수순']].map(([sort, label]) => `<button class="${state.routine.sort === sort ? 'active' : ''}" type="button" data-routine-sort="${sort}">${label}</button>`).join('')}
        </div>
      </div>
    </section>
    <section class="page-section routine-library-list">
      ${!state.routine.libraryLoaded ? '<div class="routine-loading"><span></span>보관함을 불러오고 있어요</div>' : ''}
      ${state.routine.libraryLoaded && !(library.routines || []).length ? '<div class="empty-card"><b>보관한 루틴이 없어요</b><p>숏폼 분석 결과나 AI 추천 루틴을 LIBRARY로 저장해 보세요.</p></div>' : ''}
      ${(library.routines || []).map((routine) => `<button class="library-routine-card" type="button" data-action="open-routine-detail" data-routine-id="${routine.routineId}">
        <span class="routine-type-badge ${String(routine.routineType).toLowerCase()}">${routine.routineType === 'DAY' ? 'DAY' : 'NIGHT'}</span>
        <span><b>${escapeHtml(routine.name)}</b><small>${Number(routine.stepCount || 0)}단계 · ${formatDateTime(routine.createdAt)}${routine.overallScore != null ? ` · 매칭 ${routine.overallScore}점` : ''}</small></span><strong>›</strong>
      </button>`).join('')}
    </section>`;
}

function renderRoutineCalendar() {
  const { calendarYear: year, calendarMonth: month, calendar } = state.routine;
  const firstDay = new Date(year, month - 1, 1).getDay();
  const dayCount = new Date(year, month, 0).getDate();
  const entriesByDate = new Map((calendar?.days || []).map((day) => [day.date, day]));
  const cells = [
    ...Array.from({ length: firstDay }, () => '<span class="calendar-blank"></span>'),
    ...Array.from({ length: dayCount }, (_, index) => {
      const day = index + 1;
      const date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const record = entriesByDate.get(date);
      const completed = record?.entries?.some((entry) => entry.completed);
      const selected = state.routine.selectedDateDetail?.date === date;
      return `<button class="calendar-day ${record ? 'recorded' : ''} ${completed ? 'completed' : ''} ${selected ? 'selected' : ''}" type="button" data-calendar-date="${date}"><span>${day}</span>${record ? `<i>${completed ? '✓' : '•'}</i>` : ''}</button>`;
    }),
  ];
  return `
    <section class="page-section calendar-card">
      <header><button type="button" data-action="previous-routine-month" aria-label="이전 달">‹</button><h1>${year}년 ${month}월</h1><button type="button" data-action="next-routine-month" aria-label="다음 달">›</button></header>
      <p>완료 기록 ${Number(calendar?.completedDaysCount || 0)}일</p>
      <div class="calendar-weekdays">${['일', '월', '화', '수', '목', '금', '토'].map((day) => `<span>${day}</span>`).join('')}</div>
      <div class="calendar-grid">${cells.join('')}</div>
    </section>
    ${!state.routine.calendarLoaded ? '<section class="page-section routine-loading"><span></span>캘린더를 불러오고 있어요</section>' : ''}`;
}

function renderRoutineRecordCondition(detail) {
  const condition = conditionPresentation(detail.condition);
  const memo = detail.memo || '작성한 메모가 없어요.';
  const conditionCard = condition
    ? `<div class="routine-record-condition-card">
        <span>${icon(condition.icon, condition.value)}</span>
        <b>${escapeHtml(condition.short).replaceAll('\n', '<br>')}</b>
      </div>`
    : '<div class="routine-record-condition-card empty"><span>—</span><b>컨디션 기록<br>없음</b></div>';
  return `<section class="routine-record-condition">
    <h3>피부 컨디션</h3>
    <div>${conditionCard}<p>${escapeHtml(memo)}</p></div>
  </section>`;
}

function renderRoutineRecordSelection(logs) {
  return `<section class="routine-record-picker">
    <div class="routine-record-section-heading"><h3>진행한 루틴</h3><span>확인할 루틴을 선택해 주세요</span></div>
    <div class="routine-record-picker-list">${logs.map((routine) => {
      const completion = routineCompletion(routine);
      const type = String(routine.routineType || '').toLowerCase();
      return `<button type="button" data-action="open-routine-record" data-routine-id="${escapeHtml(routine.routineId)}">
        <span class="routine-type-badge ${type}">${escapeHtml(routine.routineType || 'ROUTINE')}</span>
        <span><b>${escapeHtml(routine.name || '이름 없는 루틴')}</b><small>${completion.rate}% 완료 · ${completion.totalCount}단계</small></span>
        <strong>›</strong>
      </button>`;
    }).join('')}</div>
  </section>`;
}

function renderRoutineRecordDetail(routine, hasMultipleRoutines) {
  const completion = routineCompletion(routine);
  const steps = sortedRoutineSteps(routine);
  return `<section class="routine-record-detail">
    ${hasMultipleRoutines ? '<button class="routine-record-back" type="button" data-action="show-routine-record-list">‹ 다른 루틴 보기</button>' : ''}
    <div class="routine-record-progress-head">
      <h3>${escapeHtml(routineTypeLabel(routine.routineType))} 케어 실천도</h3>
      <strong>${completion.rate}%(${completion.completedCount}/${completion.totalCount})</strong>
    </div>
    <div class="routine-record-progress" role="progressbar" aria-label="${escapeHtml(routineTypeLabel(routine.routineType))} 케어 실천도" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${completion.rate}"><span style="width:${completion.rate}%"></span></div>
    <div class="routine-record-steps">
      <h3>진행한 루틴</h3>
      ${steps.length ? `<ol>${steps.map((step, index) => `<li class="${step.completed ? 'completed' : ''}">
        <i aria-label="${step.completed ? '완료' : '미완료'}">${step.completed ? '✓' : ''}</i>
        <img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt="">
        <span><em>${Number(step.order || index + 1)}단계</em><b>${escapeHtml(step.productName || '제품 정보 없음')}</b></span>
      </li>`).join('')}</ol>` : '<div class="routine-record-empty">기록된 루틴 단계가 없어요.</div>'}
    </div>
  </section>`;
}

function showRoutineDateDialog(detail, selectedRoutineId = null) {
  const view = routineRecordView(detail.routineLogs, selectedRoutineId);
  let routineContent;
  if (view.mode === 'empty') {
    routineContent = '<div class="routine-record-empty">이날의 루틴 기록이 없어요.</div>';
  } else if (view.mode === 'selection') {
    routineContent = renderRoutineRecordSelection(view.logs);
  } else {
    routineContent = renderRoutineRecordDetail(view.routine, view.logs.length > 1);
  }

  openDialog(dialogFrame(
    formatRoutineRecordDate(detail.date),
    `${renderRoutineRecordCondition(detail)}${routineContent}`,
  ), 'bottom-sheet routine-record-dialog');
}

function renderRoutineGenerator() {
  const generated = state.routine.generated;
  return `
    <section class="page-section routine-generator-hero">
      <img src="/assets/ai-orb.png" alt=""><span class="eyebrow">MY 인벤토리 기반</span><h1>AI가 피부 적합도가 높은<br>제품으로 루틴을 조합해요</h1><p>미리보기 단계에서는 저장하지 않으며 성분 충돌은 경고로 알려드려요.</p>
      <div class="routine-type-picker">${[['DAY', '데이 케어'], ['NIGHT', '나이트 케어']].map(([type, label]) => `<button class="${state.routine.generateType === type ? 'active' : ''}" type="button" data-generate-type="${type}">${label}</button>`).join('')}</div>
      <button class="primary-button" type="button" data-action="generate-routine">AI 루틴 미리보기</button>
    </section>
    ${generated ? `<section class="page-section generated-routine-card">
      <div class="section-heading"><h2>${escapeHtml(generated.suggestedName)}</h2><span>${generated.steps?.length || 0}단계</span></div>
      <div class="generated-step-list">${(generated.steps || []).map((step) => `<article><i>${step.order}</i><span><b>${escapeHtml(step.productName)}</b><small>${escapeHtml(categoryLabels[step.category] || step.category || '')}</small></span><strong>${Number(step.matchScore || 0)}점</strong></article>`).join('')}</div>
      ${(generated.warnings || []).length ? `<div class="routine-warning"><b>성분 조합 확인</b>${generated.warnings.map((warning) => `<p>• ${escapeHtml(warning)}</p>`).join('')}</div>` : '<div class="routine-safe">✓ 확인된 성분 충돌이 없어요.</div>'}
      <form id="generated-routine-form" class="stack-form generated-routine-form"><label>루틴 이름<input name="name" maxlength="40" value="${escapeHtml(generated.suggestedName)}" required></label><button class="primary-button" type="submit" name="saveType" value="TODAY">오늘 루틴으로 시작</button><button class="secondary-button" type="submit" name="saveType" value="LIBRARY">보관함에 저장</button></form>
    </section>` : ''}`;
}

function formatDateTime(value) {
  if (!value) return '날짜 미확인';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

function renderInventory() {
  const inventoryItems = state.inventory.items || [];
  const favoriteItems = inventoryItems.filter((item) => item.isFavorite);
  const tabItems = state.inventoryTab === 'favorites' ? favoriteItems : inventoryItems;
  const grouped = Object.groupBy
    ? Object.groupBy(tabItems, (item) => item.category || 'ETC')
    : tabItems.reduce((acc, item) => {
      const key = item.category || 'ETC';
      (acc[key] ||= []).push(item);
      return acc;
    }, {});

  return screen(`
    ${pageHeader('인벤토리', { action: '<button class="header-action" type="button" data-action="reload-inventory">새로고침</button>' })}
    <section class="inventory-hero page-section">
      <img src="/assets/inventory-hero.png" alt="">
      <div><h1>${escapeHtml(state.member?.nickname || '사용자')}님의 화장대에는<br><strong>${Number(state.inventory.totalCount || inventoryItems.length)}개</strong>의 제품이 있어요</h1><p>새로운 제품이 있거나 궁금한 제품은 인벤토리에 등록해 AI 맞춤 정보를 확인해보세요.</p></div>
      <button class="primary-button" type="button" data-action="open-add-product">새로운 제품 등록하기</button>
    </section>
    <nav class="inventory-tabs" aria-label="인벤토리 메뉴">
      <button class="${state.inventoryTab === 'all' ? 'active' : ''}" type="button" data-inventory-tab="all">라이브러리</button>
      <button class="${state.inventoryTab === 'favorites' ? 'active' : ''}" type="button" data-inventory-tab="favorites">즐겨찾기</button>
      <button class="${state.inventoryTab === 'search' ? 'active' : ''}" type="button" data-inventory-tab="search">검색</button>
    </nav>
    ${state.inventoryTab === 'search' ? renderInventorySearch() : `
      ${state.inventoryTab === 'all' ? `<section class="page-section"><div class="section-heading"><h2>즐겨찾는 화장품</h2><button type="button" data-inventory-tab="favorites">전체보기 ›</button></div>${renderProductRail(favoriteItems)}</section>` : ''}
      <section class="inventory-groups">
        ${Object.entries(grouped).length ? Object.entries(grouped).map(([category, items]) => `<section class="page-section inventory-category"><div class="section-heading"><h2>${escapeHtml(categoryLabels[category] || category)}</h2><span>${items.length}개</span></div><div class="inventory-grid"><button class="add-tile" type="button" data-action="open-add-product"><span>＋</span><small>새 제품 등록하기</small></button>${items.map(renderInventoryTile).join('')}</div></section>`).join('') : '<section class="page-section"><div class="empty-card"><b>등록된 제품이 없어요</b><p>제품명만 입력하면 카테고리와 이미지를 찾아 인벤토리에 추가합니다.</p><button class="primary-button" type="button" data-action="open-add-product">첫 제품 등록하기</button></div></section>'}
      </section>`}
  `, { active: 'inventory' });
}

function renderInventoryTile(item) {
  return `<article class="inventory-tile">
    <button class="product-open" type="button" data-inventory-id="${item.inventoryId}" data-product-id="${item.productId}"><img src="${safeImageUrl(item.imageUrl)}" alt=""><b>${escapeHtml(item.productName)}</b><small>${escapeHtml(item.brand || categoryLabels[item.category] || '')}</small></button>
    <button class="favorite-button ${item.isFavorite ? 'active' : ''}" type="button" data-action="toggle-favorite" data-inventory-id="${item.inventoryId}" data-favorite="${!item.isFavorite}" aria-label="즐겨찾기">★</button>
  </article>`;
}

function renderInventorySearch() {
  return `<section class="page-section inventory-search-section">
    <form id="product-search-form" class="search-field"><input name="query" value="${escapeHtml(state.inventorySearch)}" placeholder="제품명을 검색하세요"><button type="submit">검색</button></form>
    <p class="search-description">마스터 화장품 목록에서 검색한 뒤 바로 내 인벤토리에 등록할 수 있어요.</p>
    <div class="search-results">
      ${state.searchResults.length ? state.searchResults.map((item) => `<article class="search-result"><img src="${safeImageUrl(item.imageUrl)}" alt=""><span><b>${escapeHtml(item.productName)}</b><small>${escapeHtml(item.brand || categoryLabels[item.category] || '')}</small></span><button type="button" data-action="add-search-product" data-product-name="${escapeHtml(item.productName)}">등록</button></article>`).join('') : '<div class="empty-card compact"><b>찾고 싶은 제품이 있나요?</b><p>제품명이나 브랜드명을 입력해 검색해보세요.</p></div>'}
    </div>
  </section>`;
}

function renderProfile() {
  const member = state.member || demoMember;
  return screen(`
    <section class="profile-head page-section">
      <div class="profile-avatar"><img src="${safeImageUrl(member.profileImageUrl, '/assets/soak-logo.png')}" alt=""></div>
      <div><h1>${escapeHtml(member.nickname || '사용자')} 님</h1><p>${escapeHtml(member.skinType || '피부 타입 미설정')} · ${(member.skinConcerns || []).map(escapeHtml).join(' · ') || '피부 고민 미설정'}</p></div>
      <button type="button" data-action="edit-profile">프로필 관리</button>
      <div class="profile-tags">${(member.skinConcerns || []).map((item) => `<span>#${escapeHtml(item)}</span>`).join('')}</div>
    </section>
    <hr class="profile-divider">
    <section class="page-section profile-summary">
      <div class="section-heading"><h2>이번 달 요약</h2><span>기록보기 ›</span></div>
      <div class="summary-card"><div>${icon('summary-condition.svg')}<span>컨디션 기록</span><b>${state.home?.todayCondition?.logged ? 1 : 0}<small>회</small></b></div><div>${icon('summary-routine.svg')}<span>오늘의 루틴</span><b>${state.home?.todayRoutine ? 1 : 0}<small>개</small></b></div></div>
    </section>
    <section class="page-section settings-section">
      <h2>설정 및 계정 관리</h2>
      <button type="button" data-view="routine">${icon('settings-routine.svg')}<span>오늘의 루틴 보기</span><b>›</b></button>
      <button type="button" data-action="open-settings">${icon('settings-api.svg')}<span>API 테스트 설정</span><b>›</b></button>
      <button type="button" data-action="show-token">${icon('settings-account.svg')}<span>인증 정보 확인</span><b>›</b></button>
    </section>
    <section class="page-section settings-section support">
      <h2>고객 지원 및 앱 정보</h2>
      <a href="${escapeHtml(state.backendUrl)}/swagger-ui/index.html" target="_blank" rel="noreferrer">${icon('settings-info.svg')}<span>Swagger API 문서</span><b>↗</b></a>
      <button type="button" data-action="logout">${icon('settings-logout.svg')}<span>로그아웃</span><b>›</b></button>
    </section>
    <section class="page-section debug-section">
      <details><summary>마지막 API 응답 보기</summary><pre>${escapeHtml(JSON.stringify(state.debug, null, 2) || '아직 API 요청이 없습니다.')}</pre></details>
    </section>
  `, { active: 'profile' });
}

let previewTimer = null;
let previewAbortController = null;
let pollTimer = null;

function openDialog(content, className = '') {
  dialog.className = `app-dialog ${className}`;
  dialog.innerHTML = content;
  if (!dialog.open) dialog.showModal();
}

function closeDialog() {
  if (dialog.open) dialog.close();
  dialog.innerHTML = '';
}

function dialogFrame(title, content, actions = '') {
  return `<div class="dialog-frame"><header><h2>${escapeHtml(title)}</h2><button type="button" data-dialog-close aria-label="닫기">×</button></header><div class="dialog-body">${content}</div>${actions ? `<footer>${actions}</footer>` : ''}</div>`;
}

function showSettingsDialog() {
  openDialog(dialogFrame('API 테스트 설정', `
    <form id="settings-form" class="stack-form">
      <label>백엔드 URL<input name="backendUrl" type="url" value="${escapeHtml(state.backendUrl)}" required></label>
      <label>Access Token<textarea name="accessToken" rows="4" placeholder="Bearer 접두어 없이 JWT만 입력">${escapeHtml(state.accessToken)}</textarea></label>
      <p class="form-help">토큰은 메모리에만 보관되며 새로고침하면 사라집니다. 카카오 Client Secret이나 서버 API 키는 입력하지 마세요.</p>
      <button class="primary-button" type="submit">설정 저장 후 연결</button>
    </form>
  `), 'settings-dialog');
}

function showProfileDialog() {
  const member = state.member || demoMember;
  openDialog(dialogFrame('프로필 관리', `
    <form id="profile-form" class="stack-form">
      <label>닉네임<input name="nickname" maxlength="10" value="${escapeHtml(member.nickname || '')}" required></label>
      <label>피부 타입<select name="skinType">${skinTypes.map((item) => `<option value="${item.value}" ${item.value === member.skinType ? 'selected' : ''}>${item.title}</option>`).join('')}</select></label>
      <fieldset><legend>피부 고민</legend><div class="dialog-chip-list">${concerns.map((item) => `<label><input type="checkbox" name="skinConcerns" value="${item}" ${(member.skinConcerns || []).includes(item) ? 'checked' : ''}><span>${item}</span></label>`).join('')}</div></fieldset>
      <button class="primary-button" type="submit">저장하기</button>
    </form>
  `), 'profile-dialog');
}

function showAddProductDialog(prefill = '') {
  openDialog(dialogFrame('새로운 제품 등록', `
    <div class="dialog-illustration">${icon('inventory-plus.svg')}</div>
    <p class="dialog-lead">제품명만 입력하면 이미지와 카테고리를 찾아 내 화장대에 등록해요.</p>
    <form id="add-product-form" class="stack-form"><label>제품명<input name="productName" value="${escapeHtml(prefill)}" placeholder="예: 달바 화이트 트러플 세럼" required></label><button class="primary-button" type="submit">인벤토리에 등록</button></form>
  `), 'product-dialog');
}

function showSaveRoutineDialog() {
  openDialog(dialogFrame('루틴 저장', `
    <div class="save-routine-copy"><span>내 화장대 기반 맞춤 최적화 완료!</span><h2>이 안전한 루틴으로 오늘 케어할까요?</h2><p>저장 시 홈 화면의 오늘의 데일리 루틴에 바로 추가돼요.</p></div>
    <form id="save-routine-form" class="stack-form">
      <label>사용 시간대<select name="routineType"><option value="">현재 시간에 맞게 자동 선택</option><option value="DAY">데이 케어</option><option value="NIGHT">나이트 케어</option></select></label>
      <button class="primary-button" type="submit" name="saveType" value="TODAY">이 루틴으로 오늘 시작하기</button>
      <button class="secondary-button" type="submit" name="saveType" value="LIBRARY">나의 루틴 보관함에 저장</button>
    </form>
  `), 'bottom-sheet');
}

function showTokenDialog() {
  openDialog(dialogFrame('인증 정보', `
    <dl class="token-info"><div><dt>모드</dt><dd>${state.isDemo ? 'UI 미리보기' : '실제 API 연결'}</dd></div><div><dt>백엔드</dt><dd>${escapeHtml(state.backendUrl)}</dd></div><div><dt>회원 ID</dt><dd>${escapeHtml(state.member?.id || '-')}</dd></div></dl>
    <label class="token-field">Access Token<textarea readonly rows="6">${escapeHtml(state.accessToken || '미리보기 모드에는 토큰이 없습니다.')}</textarea></label>
    <button class="secondary-button" type="button" data-action="copy-token" ${state.accessToken ? '' : 'disabled'}>토큰 복사</button>
  `));
}

async function showProductDetail({ inventoryId, productId, resultId }) {
  openDialog(dialogFrame('제품 분석', '<div class="modal-loading"><span></span> 제품 정보를 불러오고 있어요</div>'), 'product-detail-dialog');
  try {
    let detail;
    let ingredients = null;
    if (state.isDemo) {
      detail = demoProductDetail;
    } else if (resultId && state.analysisId) {
      const data = await api.data(`/api/shortform-analyses/${state.analysisId}/results/${resultId}`);
      detail = data.result;
      detail.disclaimer = data.disclaimer;
    } else if (inventoryId) {
      const [analysis, ingredientResult] = await Promise.allSettled([
        api.data(`/api/v1/inventory/${inventoryId}/ai-analysis`),
        api.data(`/api/v1/inventory/${inventoryId}/ingredients`),
      ]);
      if (analysis.status === 'rejected' && ingredientResult.status === 'rejected') throw analysis.reason;
      const item = state.inventory.items.find((candidate) => String(candidate.inventoryId) === String(inventoryId)) || {};
      const analysisData = analysis.status === 'fulfilled' ? analysis.value : {};
      ingredients = ingredientResult.status === 'fulfilled' ? ingredientResult.value : null;
      detail = {
        displayBrand: item.brand,
        displayProductName: analysisData.productName || item.productName,
        category: categoryLabels[item.category] || item.category,
        imageUrl: item.imageUrl,
        matchScore: analysisData.score,
        reasons: (analysisData.keywords || []).map((entry) => ({ title: entry.keyword, description: entry.reason, assessmentCategory: 'BENEFICIAL' })),
      };
    } else if (productId) {
      detail = await api.data(`/api/v1/products/${productId}/skin-analysis`);
      detail.displayProductName = detail.productName;
      detail.matchScore = detail.matchScore;
      detail.reasons = detail.aiAnalysis?.reasons?.map((entry) => ({ title: entry.keyword, description: entry.reason, assessmentCategory: 'CAUTION' })) || [];
    } else {
      throw new Error('제품 식별자가 없습니다.');
    }
    state.debug = detail;
    renderProductDetailDialog(detail, {
      inventoryId,
      ingredients,
      analysisId: resultId ? state.analysisId : null,
      resultId,
    });
  } catch (error) {
    openDialog(dialogFrame('제품 분석', `<div class="dialog-error"><b>제품 정보를 불러오지 못했어요.</b><p>${escapeHtml(error.message)}</p></div>`));
  }
}

function renderProductDetailDialog(detail, {
  inventoryId,
  ingredients,
  analysisId,
  resultId,
} = {}) {
  const score = detail.matchScore ?? detail.score;
  const reasons = detail.reasons || [];
  const ingredientItems = detail.ingredients || ingredients?.ingredients || [];
  const productChips = [
    detail.ingredientMarketOrVariant,
    categoryLabels[detail.category] || detail.category,
  ].filter(Boolean);
  const canReanalyzeIngredients = analysisId && resultId && needsIngredientReanalysis(detail);
  const reasonCards = reasons.map((reason) => {
    const presentation = reasonPresentation(reason);
    return `<article class="reason-card ${presentation.category}"><span class="reason-card-icon"><img src="${presentation.iconUrl}" alt=""></span><div><b>${escapeHtml(reason.title || reason.keyword)}</b><p>${escapeHtml(reason.description || reason.reason)}</p></div></article>`;
  }).join('');
  openDialog(dialogFrame('제품 상세', `
    <section class="product-detail-head"><img src="${safeImageUrl(detail.imageUrl)}" alt=""><div><small>${escapeHtml(detail.displayBrand || detail.brand || '브랜드 미확인')}</small><h2>${escapeHtml(detail.displayProductName || detail.productName || '제품명 미확인')}</h2><div class="product-detail-chips">${productChips.map((chip) => `<span>${escapeHtml(chip)}</span>`).join('')}</div></div></section>
    ${score == null ? '' : `<div class="personal-score"><img src="/assets/ai-orb.png" alt=""><span><small>내 피부 프로필 맞춤</small><b>AI 매칭 점수 ${Number(score)}점</b></span></div>`}
    <div class="detail-tabs"><button class="active" type="button" data-detail-tab="analysis">AI 맞춤 분석</button><button type="button" data-detail-tab="ingredients">전체 성분</button></div>
    <section class="detail-pane active" data-detail-pane="analysis"><h3>이 제품이 ${score ?? '-'}점인 이유</h3>${reasons.length ? reasonCards : '<div class="empty-inline">분석 근거가 제공되지 않았어요.</div>'}${detail.disclaimer ? `<p class="disclaimer">${escapeHtml(detail.disclaimer)}</p>` : ''}</section>
    <section class="detail-pane" data-detail-pane="ingredients"><h3>전성분 ${ingredientItems.length}개</h3>${ingredientItems.length ? `<ul class="ingredient-list">${ingredientItems.map((item, index) => `<li><i class="risk-${String(item.riskLevel || 'unknown').toLowerCase()}">${item.riskScore ?? index + 1}</i><span><b>${escapeHtml(item.name || item.ingredientName)}</b><small>${escapeHtml((item.purposes || item.purposeTags || []).join(', ') || '배합 목적 미확인')}</small></span></li>`).join('')}</ul>` : `<div class="empty-inline ingredient-reanalysis"><p>전체 성분 정보가 아직 없어요.</p>${canReanalyzeIngredients ? `<button class="secondary-button" type="button" data-action="reanalyze-ingredients" data-analysis-id="${analysisId}">성분 정보 다시 분석하기</button><small>확인된 캐시는 유지하고 비어 있는 제품만 다시 확인해요.</small>` : ''}</div>`}</section>
    ${inventoryId ? `<button class="danger-text" type="button" data-action="delete-inventory" data-inventory-id="${inventoryId}">인벤토리에서 삭제</button>` : ''}
  `), 'product-detail-dialog');
}

async function reanalyzeIngredients(analysisId) {
  if (!analysisId || state.isDemo) return;
  const button = dialog.querySelector('[data-action="reanalyze-ingredients"]');
  if (button) button.disabled = true;
  try {
    const created = await api.data(`/api/shortform-analyses/${analysisId}/reanalyze-ingredients`, {
      method: 'POST',
    });
    closeDialog();
    state.analysisId = created.analysisId;
    state.analysisStatus = created;
    state.analysisResult = null;
    state.optimization = null;
    state.activeView = 'analysis';
    state.debug = created;
    render();
    toast(created.reused ? '진행 중인 성분 재분석을 이어서 확인해요.' : '비어 있는 성분 정보를 다시 분석하고 있어요.', 'success');
    if (created.status === 'COMPLETED') await loadAnalysisDetail(created.analysisId);
    else pollAnalysis();
  } catch (error) {
    toast(`성분 재분석 요청에 실패했습니다. ${error.message}`, 'error');
    if (button) button.disabled = false;
  }
}

async function hydrateApp({ forceOnboarding = false } = {}) {
  state.routine = createRoutineState();
  if (state.isDemo) {
    state.member = structuredClone(demoMember);
    state.home = structuredClone(demoHome);
    state.inventory = { totalCount: demoProducts.length, items: structuredClone(demoProducts) };
    state.history = structuredClone(demoHistory);
    state.activeView = forceOnboarding ? 'onboarding' : 'home';
    state.onboarding.nickname = state.member.nickname;
    render();
    return;
  }

  setBusy(true);
  try {
    const member = await api.data('/api/members/me');
    state.member = member;
    state.onboarding = {
      skinType: member.skinType || '건성',
      skinConcerns: member.skinConcerns?.length ? [...member.skinConcerns] : ['속건조'],
      nickname: member.nickname || '',
    };
    const needsOnboarding = forceOnboarding || !member.skinType;
    state.activeView = needsOnboarding ? 'onboarding' : 'home';
    render();
    if (!needsOnboarding) await loadAppData();
  } catch (error) {
    state.activeView = 'splash';
    render();
    toast(`연결에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function loadAppData() {
  const results = await Promise.allSettled([loadHome(false), loadInventory(false), loadHistory(false)]);
  const failed = results.filter((result) => result.status === 'rejected');
  render();
  if (failed.length) toast(`일부 데이터를 불러오지 못했어요. (${failed.length}개 요청)`, 'error');
}

async function loadHome(shouldRender = true) {
  if (state.isDemo) state.home = structuredClone(demoHome);
  else state.home = await api.data('/api/v1/home');
  if (shouldRender) render();
}

async function loadInventory(shouldRender = true) {
  if (state.isDemo) state.inventory = { totalCount: demoProducts.length, items: structuredClone(demoProducts) };
  else state.inventory = await api.data('/api/v1/inventory');
  if (shouldRender) render();
}

async function loadHistory(shouldRender = true) {
  if (state.isDemo) state.history = structuredClone(demoHistory);
  else state.history = (await api.data('/api/shortform-analyses')).items || [];
  if (shouldRender) render();
}

function syncDailyRoutineToHome(routine) {
  if (!state.home) return;
  state.home.todayRoutine = routine
    ? {
      routineId: routine.routineId,
      name: routine.name,
      routineType: routine.routineType,
      steps: routine.steps,
    }
    : null;
}

async function loadDailyRoutine(shouldRender = true) {
  state.routine.daily = state.isDemo
    ? structuredClone(demoDailyRoutine)
    : await api.data('/api/v1/routines/daily');
  state.routine.dailyLoaded = true;
  syncDailyRoutineToHome(state.routine.daily);
  if (shouldRender) render();
}

async function loadRoutineLibrary(shouldRender = true) {
  state.routine.library = state.isDemo
    ? structuredClone(demoRoutineLibrary)
    : await api.data(`/api/v1/routines?status=ARCHIVED${state.routine.year ? `&year=${state.routine.year}` : ''}&sort=${encodeURIComponent(state.routine.sort)}`);
  state.routine.libraryLoaded = true;
  if (shouldRender) render();
}

async function loadRoutineCalendar(shouldRender = true) {
  const { calendarYear: year, calendarMonth: month } = state.routine;
  state.routine.calendar = state.isDemo
    ? { ...structuredClone(demoRoutineCalendar), year, month, days: demoRoutineCalendar.year === year && demoRoutineCalendar.month === month ? structuredClone(demoRoutineCalendar.days) : [], completedDaysCount: demoRoutineCalendar.year === year && demoRoutineCalendar.month === month ? demoRoutineCalendar.completedDaysCount : 0 }
    : await api.data(buildRoutineLogsPath({ year, month }));
  state.routine.calendarLoaded = true;
  state.routine.selectedDateDetail = null;
  if (shouldRender) render();
}

async function loadRoutineDate(date) {
  setBusy(true);
  try {
    state.routine.selectedDateDetail = state.isDemo
      ? createDemoRoutineDateDetail(date)
      : await api.data(buildRoutineLogsPath({ date }));
    state.debug = state.routine.selectedDateDetail;
    render();
    showRoutineDateDialog(state.routine.selectedDateDetail);
  } catch (error) {
    toast(`날짜별 루틴 기록을 불러오지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function loadRoutineTab(tab = state.routine.tab, { force = false } = {}) {
  if (tab === 'today' && (force || !state.routine.dailyLoaded)) await loadDailyRoutine();
  else if (tab === 'library' && (force || !state.routine.libraryLoaded)) await loadRoutineLibrary();
  else if (tab === 'calendar' && (force || !state.routine.calendarLoaded)) await loadRoutineCalendar();
}

async function reloadCurrentRoutineTab() {
  try {
    await loadRoutineTab(state.routine.tab, { force: true });
    toast('루틴 데이터를 새로 불러왔어요.', 'success');
  } catch (error) {
    toast(`루틴 데이터를 불러오지 못했어요. ${error.message}`, 'error');
  }
}

async function beginKakaoLogin() {
  if (!kakaoConfig.clientId || !kakaoConfig.formatIsValid) {
    toast('먼저 .env.local에 올바른 카카오 REST API 키를 설정해 주세요.', 'error');
    return;
  }
  try {
    const redirect = new URL(state.redirectUri);
    if (!['http:', 'https:'].includes(redirect.protocol)) throw new Error('지원하지 않는 프로토콜');
  } catch {
    toast('VITE_KAKAO_REDIRECT_URI 설정을 확인해 주세요.', 'error');
    return;
  }
  const oauthState = createOAuthState();
  sessionStorage.setItem('soak-fe-test-oauth-state', oauthState);
  window.location.assign(buildKakaoAuthorizeUrl({
    clientId: kakaoConfig.clientId,
    redirectUri: state.redirectUri,
    state: oauthState,
  }));
}

async function exchangeKakaoCode(code) {
  setBusy(true);
  try {
    const payload = await api.data('/api/auth/kakao/login', {
      method: 'POST',
      auth: false,
      body: JSON.stringify({ code, redirectUri: state.redirectUri }),
    });
    state.accessToken = payload.accessToken;
    state.refreshToken = payload.refreshToken;
    state.member = payload.member;
    state.debug = payload;
    await hydrateApp({ forceOnboarding: payload.isNewMember || !payload.member?.skinType });
    toast('카카오 로그인에 성공했어요.', 'success');
  } catch (error) {
    state.activeView = 'splash';
    render();
    toast(`카카오 로그인에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function completeOnboarding() {
  const payload = {
    nickname: state.onboarding.nickname.trim(),
    skinType: state.onboarding.skinType,
    skinConcerns: state.onboarding.skinConcerns,
  };
  if (!payload.nickname) return;
  setBusy(true);
  try {
    state.member = state.isDemo
      ? { ...state.member, ...payload }
      : await api.data('/api/members/me', { method: 'PATCH', body: JSON.stringify(payload) });
    state.activeView = 'home';
    await loadAppData();
    toast('맞춤 피부 프로필을 저장했어요.', 'success');
  } catch (error) {
    toast(`프로필 저장에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function loadPreview() {
  const url = state.videoUrl.trim();
  if (!url) {
    state.preview = null;
    render();
    return;
  }
  previewAbortController?.abort();
  previewAbortController = new AbortController();
  state.previewLoading = true;
  state.preview = null;
  render();
  try {
    state.preview = state.isDemo
      ? structuredClone(demoPreview)
      : await api.data('/api/shortform-analyses/preview', {
        method: 'POST',
        body: JSON.stringify({ videoUrl: url }),
        signal: previewAbortController.signal,
      });
    state.debug = state.preview;
  } catch (error) {
    if (error.name !== 'AbortError') toast(`영상 정보를 불러오지 못했어요. ${error.message}`, 'error');
  } finally {
    state.previewLoading = false;
    render();
  }
}

function schedulePreview() {
  if (previewTimer) window.clearTimeout(previewTimer);
  previewTimer = window.setTimeout(loadPreview, 450);
}

async function startAnalysis() {
  if (!state.videoUrl.trim()) return;
  setBusy(true);
  try {
    if (state.isDemo) {
      state.analysisId = 301;
      state.analysisStatus = { status: 'EXTRACTING_VIDEO', progress: 18, message: '영상에서 제품을 찾고 있어요.' };
      render();
      simulateDemoAnalysis();
      return;
    }
    const created = await api.data('/api/shortform-analyses', {
      method: 'POST',
      body: JSON.stringify({ videoUrl: state.videoUrl.trim() }),
    });
    state.analysisId = created.analysisId;
    state.analysisStatus = created;
    state.analysisResult = null;
    state.optimization = null;
    state.debug = created;
    render();
    if (created.status === 'COMPLETED') await loadAnalysisDetail(created.analysisId);
    else pollAnalysis();
  } catch (error) {
    toast(`분석 요청에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

function simulateDemoAnalysis() {
  const phases = [
    { status: 'MATCHING_PRODUCTS', progress: 42, message: '제품과 성분 정보를 확인하고 있어요.' },
    { status: 'PERSONALIZING', progress: 68, message: '피부 프로필에 맞춰 분석하고 있어요.' },
    { status: 'OPTIMIZING', progress: 88, message: '분석 결과를 정리하고 있어요.' },
  ];
  phases.forEach((phase, index) => window.setTimeout(() => {
    state.analysisStatus = phase;
    render();
  }, 700 * (index + 1)));
  window.setTimeout(() => {
    state.analysisStatus = { status: 'COMPLETED', progress: 100, message: '분석이 완료됐어요.' };
    state.analysisResult = structuredClone(demoAnalysisResult);
    state.history = structuredClone(demoHistory);
    render();
  }, 2900);
}

function stopPolling() {
  if (pollTimer) window.clearTimeout(pollTimer);
  pollTimer = null;
}

async function pollAnalysis() {
  if (!state.analysisId || state.isDemo) return;
  stopPolling();
  try {
    const status = await api.data(`/api/shortform-analyses/${state.analysisId}/status`);
    state.analysisStatus = status;
    state.debug = status;
    render();
    if (status.status === 'COMPLETED') await loadAnalysisDetail(state.analysisId);
    else if (!['FAILED', 'CANCELLED'].includes(status.status)) pollTimer = window.setTimeout(pollAnalysis, 1500);
    else toast(status.errorMessage || status.message || '분석이 종료됐어요.', status.status === 'FAILED' ? 'error' : 'info');
  } catch (error) {
    toast(`분석 상태를 확인하지 못했어요. ${error.message}`, 'error');
  }
}

async function loadAnalysisDetail(analysisId) {
  setBusy(true);
  try {
    const detail = state.isDemo
      ? { result: structuredClone(demoAnalysisResult) }
      : await api.data(`/api/shortform-analyses/${analysisId}`);
    state.analysisId = Number(analysisId);
    state.analysisStatus = { status: 'COMPLETED', progress: 100 };
    state.analysisResult = detail.result;
    state.optimization = null;
    state.debug = detail;
    state.activeView = 'analysis';
    render();
    if (!state.isDemo) loadHistory(false).catch(() => {});
  } catch (error) {
    toast(`분석 결과를 불러오지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function optimizeAnalysis() {
  if (!state.analysisId) return;
  setBusy(true);
  try {
    const data = state.isDemo
      ? { result: structuredClone(demoOptimization) }
      : await api.data(`/api/shortform-analyses/${state.analysisId}/optimize`, { method: 'POST' });
    state.optimization = data.result;
    state.debug = data;
    render();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (error) {
    toast(`인벤토리 최적화에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function saveRoutine(saveType, routineType) {
  if (!state.analysisId) return;
  setBusy(true);
  try {
    const payload = buildRoutineApplyPayload(saveType, routineType);
    const data = state.isDemo
      ? { analysisId: state.analysisId, routineId: 21, saveType, routineType: routineType || 'NIGHT', routineStatus: 'ACTIVE', reused: false }
      : await api.data(`/api/shortform-analyses/${state.analysisId}/apply`, { method: 'POST', body: JSON.stringify(payload) });
    state.savedRoutine = data;
    state.debug = data;
    closeDialog();
    if (!state.isDemo) await Promise.all([loadHome(false), loadDailyRoutine(false), loadRoutineLibrary(false)]);
    toast(saveType === 'TODAY' ? '오늘의 루틴으로 저장했어요.' : '루틴 보관함에 저장했어요.', 'success');
    state.activeView = saveType === 'TODAY' ? 'routine' : 'analysis';
    render();
  } catch (error) {
    toast(`루틴 저장에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

function setDailyRoutine(routine) {
  state.routine.daily = routine;
  state.routine.dailyLoaded = true;
  syncDailyRoutineToHome(routine);
  state.debug = routine;
}

function recalculateDemoDailyRoutine(routine) {
  const completedCount = (routine.steps || []).filter((step) => step.completed).length;
  return {
    ...routine,
    completionRate: routine.steps?.length ? Math.round((completedCount * 100) / routine.steps.length) : 0,
  };
}

async function toggleRoutineStep(stepId, completed) {
  if (!stepId) return;
  setBusy(true);
  try {
    let routine;
    if (state.isDemo) {
      routine = recalculateDemoDailyRoutine({
        ...state.routine.daily,
        completed: false,
        steps: state.routine.daily.steps.map((step) => String(step.stepId) === String(stepId) ? { ...step, completed } : step),
      });
    } else {
      routine = await api.data(`/api/v1/routine-logs/today/steps/${stepId}`, {
        method: 'PATCH',
        body: JSON.stringify({ completed }),
      });
    }
    setDailyRoutine(routine);
    render();
  } catch (error) {
    toast(`단계 완료 상태를 바꾸지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function completeAllRoutineSteps() {
  setBusy(true);
  try {
    const routine = state.isDemo
      ? { ...state.routine.daily, completionRate: 100, steps: state.routine.daily.steps.map((step) => ({ ...step, completed: true })) }
      : await api.data('/api/v1/routine-logs/today/steps/complete-all', { method: 'POST' });
    setDailyRoutine(routine);
    render();
    toast('모든 단계를 체크했어요.', 'success');
  } catch (error) {
    toast(`전체 단계를 완료하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function completeTodayRoutine() {
  setBusy(true);
  try {
    const routine = state.isDemo
      ? { ...state.routine.daily, completed: true, completionRate: 100 }
      : await api.data('/api/v1/routine-logs/today/complete', { method: 'POST' });
    setDailyRoutine(routine);
    state.routine.calendarLoaded = false;
    render();
    toast('오늘의 루틴을 완료했어요!', 'success');
  } catch (error) {
    toast(`오늘의 루틴을 완료하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function showRoutineDetail(routineId) {
  openDialog(dialogFrame('보관함 루틴', '<div class="modal-loading"><span></span> 루틴을 불러오고 있어요</div>'), 'routine-detail-dialog');
  try {
    const detail = state.isDemo
      ? structuredClone(demoRoutineDetails[routineId])
      : await api.data(`/api/v1/routines/${routineId}`);
    if (!detail) throw new Error('루틴 정보를 찾을 수 없습니다.');
    state.debug = detail;
    openDialog(dialogFrame('보관함 루틴', `
      <section class="routine-detail-head"><span class="routine-type-badge ${String(detail.routineType).toLowerCase()}">${detail.routineType}</span><h2>${escapeHtml(detail.name)}</h2><p>${detail.steps?.length || 0}단계 · ${escapeHtml(detail.status)}</p></section>
      ${detail.aiBriefing ? renderBriefing(detail.aiBriefing) : ''}
      <div class="routine-detail-steps">${(detail.steps || []).map((step) => `<article><i>${step.order}</i><img src="${safeImageUrl(step.imageUrl, '/assets/product-jar.png')}" alt=""><span><b>${escapeHtml(step.productName)}</b><small>${escapeHtml(categoryLabels[step.category] || step.category || step.brand || '')}</small>${step.aiReason ? `<em>${escapeHtml(step.aiReason)}</em>` : ''}</span></article>`).join('')}</div>
    `, `<div class="dialog-action-stack"><button class="primary-button" type="button" data-action="apply-routine-today" data-routine-id="${detail.routineId}">오늘 루틴으로 적용</button><button class="danger-text" type="button" data-action="delete-routine" data-routine-id="${detail.routineId}">이 루틴 삭제</button></div>`), 'routine-detail-dialog');
  } catch (error) {
    openDialog(dialogFrame('보관함 루틴', `<div class="dialog-error"><b>루틴을 불러오지 못했어요.</b><p>${escapeHtml(error.message)}</p></div>`));
  }
}

async function applyArchivedRoutine(routineId) {
  setBusy(true);
  try {
    let routine;
    if (state.isDemo) {
      const detail = demoRoutineDetails[routineId];
      routine = {
        ...structuredClone(detail),
        completed: false,
        completionRate: 0,
        steps: detail.steps.map((step, index) => ({ ...step, stepId: Date.now() + index, completed: false })),
      };
      state.routine.library.routines = state.routine.library.routines.filter((item) => String(item.routineId) !== String(routineId));
      state.routine.library.totalCount = state.routine.library.routines.length;
    } else {
      routine = await api.data(`/api/v1/routines/${routineId}/apply-today`, { method: 'POST' });
      await Promise.all([loadHome(false), loadRoutineLibrary(false)]);
    }
    setDailyRoutine(routine);
    state.routine.tab = 'today';
    closeDialog();
    render();
    toast('보관함 루틴을 오늘 케어로 적용했어요.', 'success');
  } catch (error) {
    toast(`루틴을 적용하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function deleteArchivedRoutine(routineId) {
  if (!window.confirm('이 루틴을 보관함에서 삭제할까요?')) return;
  setBusy(true);
  try {
    const data = state.isDemo
      ? { routineId: Number(routineId) }
      : await api.data(`/api/v1/routines/${routineId}`, { method: 'DELETE' });
    state.routine.library.routines = state.routine.library.routines.filter((item) => String(item.routineId) !== String(routineId));
    state.routine.library.totalCount = state.routine.library.routines.length;
    state.debug = data;
    closeDialog();
    render();
    toast('보관함에서 루틴을 삭제했어요.', 'success');
  } catch (error) {
    toast(`루틴을 삭제하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function changeRoutineMonth(offset) {
  const date = new Date(state.routine.calendarYear, state.routine.calendarMonth - 1 + offset, 1);
  state.routine.calendarYear = date.getFullYear();
  state.routine.calendarMonth = date.getMonth() + 1;
  state.routine.calendarLoaded = false;
  state.routine.selectedDateDetail = null;
  render();
  try {
    await loadRoutineCalendar();
  } catch (error) {
    toast(`캘린더를 불러오지 못했어요. ${error.message}`, 'error');
  }
}

async function generateRoutinePreview() {
  setBusy(true);
  try {
    state.routine.generated = state.isDemo
      ? { ...structuredClone(demoGeneratedRoutine), routineType: state.routine.generateType, suggestedName: state.routine.generateType === 'DAY' ? 'AI 추천 데이 루틴' : 'AI 추천 나이트 루틴' }
      : await api.data(`/api/v1/routines/generate?routineType=${encodeURIComponent(state.routine.generateType)}`, { method: 'POST' });
    state.debug = state.routine.generated;
    render();
    window.setTimeout(() => root.querySelector('.generated-routine-card')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 0);
  } catch (error) {
    toast(`AI 루틴을 생성하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function createGeneratedRoutine(name, saveType) {
  const payload = buildGeneratedRoutineCreatePayload({ generated: state.routine.generated, name, saveType });
  setBusy(true);
  try {
    const data = state.isDemo
      ? { routineId: Date.now(), name: payload.name, routineType: payload.routineType, status: saveType === 'TODAY' ? 'ACTIVE' : 'ARCHIVED' }
      : await api.data('/api/v1/routines', { method: 'POST', body: JSON.stringify(payload) });
    state.debug = data;
    if (state.isDemo && saveType === 'LIBRARY') {
      state.routine.library.routines.unshift({ ...data, stepCount: payload.steps.length, createdAt: new Date().toISOString() });
      state.routine.library.totalCount = state.routine.library.routines.length;
      state.routine.libraryLoaded = true;
    } else if (state.isDemo) {
      setDailyRoutine({
        ...data,
        completed: false,
        completionRate: 0,
        steps: state.routine.generated.steps.map((step, index) => ({ ...step, stepId: Date.now() + index, completed: false, imageUrl: '/assets/product-jar.png' })),
      });
    } else {
      await Promise.all([loadHome(false), loadDailyRoutine(false), loadRoutineLibrary(false)]);
    }
    state.routine.tab = saveType === 'TODAY' ? 'today' : 'library';
    state.routine.generated = null;
    render();
    toast(saveType === 'TODAY' ? 'AI 루틴으로 오늘 케어를 시작했어요.' : 'AI 루틴을 보관함에 저장했어요.', 'success');
  } catch (error) {
    toast(`AI 루틴을 저장하지 못했어요. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function cancelAnalysis() {
  if (!state.analysisId) return;
  if (state.isDemo) {
    state.analysisStatus = null;
    render();
    toast('미리보기 분석을 취소했어요.');
    return;
  }
  try {
    const status = await api.data(`/api/shortform-analyses/${state.analysisId}/cancel`, { method: 'POST' });
    stopPolling();
    state.analysisStatus = status;
    render();
    toast(status.message || '분석을 취소했어요.');
  } catch (error) {
    toast(`분석 취소에 실패했습니다. ${error.message}`, 'error');
  }
}

async function updateCondition(memo) {
  const current = state.home?.todayCondition || {};
  const request = { condition: current.condition || null, memo: memo.trim() || null };
  if (!request.condition) {
    toast('먼저 오늘의 피부 컨디션을 선택해 주세요.', 'error');
    return;
  }
  setBusy(true);
  try {
    const data = state.isDemo
      ? { logged: true, condition: request.condition, memo: request.memo }
      : await api.data('/api/v1/home/condition', { method: 'POST', body: JSON.stringify(request) });
    state.home.todayCondition = data;
    state.debug = data;
    render();
    toast('오늘의 피부 컨디션을 기록했어요.', 'success');
  } catch (error) {
    toast(`컨디션 기록에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function toggleFavorite(inventoryId, isFavorite) {
  setBusy(true);
  try {
    const data = state.isDemo
      ? { inventoryId: Number(inventoryId), isFavorite }
      : await api.data(`/api/v1/inventory/${inventoryId}/favorite`, { method: 'PATCH', body: JSON.stringify({ isFavorite }) });
    const item = state.inventory.items.find((candidate) => String(candidate.inventoryId) === String(inventoryId));
    if (item) item.isFavorite = data.isFavorite;
    if (state.home?.favoriteInventory) {
      state.home.favoriteInventory.items = state.inventory.items.filter((candidate) => candidate.isFavorite).slice(0, 4);
      state.home.favoriteInventory.totalFavoriteCount = state.inventory.items.filter((candidate) => candidate.isFavorite).length;
    }
    state.debug = data;
    render();
  } catch (error) {
    toast(`즐겨찾기 변경에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function addProduct(productName) {
  setBusy(true);
  try {
    const data = state.isDemo
      ? { inventoryId: Date.now(), productId: Date.now(), productName, brand: '브랜드 확인 중', category: 'ETC', imageUrl: '', isFavorite: false }
      : await api.data('/api/v1/inventory', { method: 'POST', body: JSON.stringify({ productName }) });
    state.debug = data;
    closeDialog();
    if (state.isDemo) {
      state.inventory.items.unshift(data);
      state.inventory.totalCount = state.inventory.items.length;
    } else await loadInventory(false);
    state.inventoryTab = 'all';
    render();
    toast(`${data.productName || productName}을(를) 등록했어요.`, 'success');
  } catch (error) {
    toast(`제품 등록에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function deleteInventory(inventoryId) {
  if (!window.confirm('이 제품을 인벤토리에서 삭제할까요?')) return;
  setBusy(true);
  try {
    const data = state.isDemo
      ? { inventoryId: Number(inventoryId), deleted: true }
      : await api.data(`/api/v1/inventory/${inventoryId}`, { method: 'DELETE' });
    state.inventory.items = state.inventory.items.filter((item) => String(item.inventoryId) !== String(inventoryId));
    state.inventory.totalCount = state.inventory.items.length;
    state.debug = data;
    closeDialog();
    render();
    toast('인벤토리에서 제품을 삭제했어요.', 'success');
  } catch (error) {
    toast(`제품 삭제에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function searchProducts(query) {
  state.inventorySearch = query.trim();
  if (!state.inventorySearch) {
    state.searchResults = [];
    render();
    return;
  }
  setBusy(true);
  try {
    state.searchResults = state.isDemo
      ? demoProducts.filter((item) => `${item.productName} ${item.brand}`.includes(state.inventorySearch))
      : (await api.data(`/api/v1/products/search?keyword=${encodeURIComponent(state.inventorySearch)}`)).items || [];
    render();
  } catch (error) {
    toast(`제품 검색에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

async function saveProfile(formData) {
  const payload = {
    nickname: String(formData.get('nickname') || '').trim(),
    skinType: String(formData.get('skinType') || ''),
    skinConcerns: formData.getAll('skinConcerns').map(String),
  };
  setBusy(true);
  try {
    state.member = state.isDemo
      ? { ...state.member, ...payload }
      : await api.data('/api/members/me', { method: 'PATCH', body: JSON.stringify(payload) });
    state.debug = state.member;
    closeDialog();
    render();
    toast('프로필을 저장했어요.', 'success');
  } catch (error) {
    toast(`프로필 저장에 실패했습니다. ${error.message}`, 'error');
  } finally {
    setBusy(false);
  }
}

function logout() {
  const finish = () => {
    stopPolling();
    state.accessToken = '';
    state.refreshToken = '';
    state.member = null;
    state.isDemo = false;
    state.home = null;
    state.routine = createRoutineState();
    state.inventory = { totalCount: 0, items: [] };
    state.history = [];
    state.analysisStatus = null;
    state.analysisResult = null;
    state.optimization = null;
    state.activeView = 'splash';
    render();
  };
  if (state.isDemo || !state.accessToken) {
    finish();
    return;
  }
  api.data('/api/auth/logout', { method: 'POST' })
    .catch((error) => toast(`서버 로그아웃 응답: ${error.message}`, 'error'))
    .finally(finish);
}

root.addEventListener('click', async (event) => {
  const target = event.target.closest('button, [data-view]');
  if (!target) return;

  const view = target.dataset.view;
  if (view) {
    state.activeView = view;
    render();
    if (view === 'home' && !state.home) loadHome().catch((error) => toast(error.message, 'error'));
    if (view === 'inventory' && !state.inventory.items.length) loadInventory().catch((error) => toast(error.message, 'error'));
    if (view === 'routine') loadRoutineTab().catch((error) => toast(`루틴을 불러오지 못했어요. ${error.message}`, 'error'));
    return;
  }

  const routineTab = target.dataset.routineTab;
  if (routineTab) {
    state.routine.tab = routineTab;
    render();
    loadRoutineTab(routineTab).catch((error) => toast(`루틴을 불러오지 못했어요. ${error.message}`, 'error'));
    return;
  }

  const routineSort = target.dataset.routineSort;
  if (routineSort) {
    state.routine.sort = routineSort;
    state.routine.libraryLoaded = false;
    render();
    loadRoutineLibrary().catch((error) => toast(`보관함을 불러오지 못했어요. ${error.message}`, 'error'));
    return;
  }

  const generateType = target.dataset.generateType;
  if (generateType) {
    state.routine.generateType = generateType;
    state.routine.generated = null;
    render();
    return;
  }

  const calendarDate = target.dataset.calendarDate;
  if (calendarDate) {
    await loadRoutineDate(calendarDate);
    return;
  }

  const action = target.dataset.action;
  if (action === 'kakao-login') beginKakaoLogin();
  else if (action === 'demo-login') {
    state.isDemo = true;
    state.activeView = 'home';
    await hydrateApp();
    toast('UI 미리보기 모드입니다. 쓰기 동작은 로컬 데이터에만 반영돼요.');
  } else if (action === 'open-settings') showSettingsDialog();
  else if (action === 'onboarding-back') {
    if (state.onboardingStep > 0) state.onboardingStep -= 1;
    else state.activeView = 'splash';
    render();
  } else if (action === 'onboarding-next') {
    state.onboardingStep = Math.min(2, state.onboardingStep + 1);
    render();
  } else if (action === 'clear-nickname') {
    state.onboarding.nickname = '';
    render();
  } else if (action === 'complete-onboarding') completeOnboarding();
  else if (action === 'paste-url') {
    try {
      state.videoUrl = (await navigator.clipboard.readText()).trim();
      render();
      schedulePreview();
    } catch {
      toast('클립보드를 읽지 못했어요. URL을 직접 붙여넣어 주세요.', 'error');
    }
  } else if (action === 'reload-history') loadHistory().catch((error) => toast(error.message, 'error'));
  else if (action === 'reload-routine') reloadCurrentRoutineTab();
  else if (action === 'toggle-routine-step') toggleRoutineStep(target.dataset.stepId, target.dataset.completed !== 'true');
  else if (action === 'complete-all-routine-steps') completeAllRoutineSteps();
  else if (action === 'complete-today-routine') completeTodayRoutine();
  else if (action === 'open-routine-detail') showRoutineDetail(target.dataset.routineId);
  else if (action === 'previous-routine-month') changeRoutineMonth(-1);
  else if (action === 'next-routine-month') changeRoutineMonth(1);
  else if (action === 'generate-routine') generateRoutinePreview();
  else if (action === 'cancel-analysis') cancelAnalysis();
  else if (action === 'optimize-analysis') optimizeAnalysis();
  else if (action === 'open-save-routine') showSaveRoutineDialog();
  else if (action === 'show-favorites') {
    state.inventoryTab = 'favorites';
    state.activeView = 'inventory';
    render();
  } else if (action === 'reload-inventory') loadInventory().catch((error) => toast(error.message, 'error'));
  else if (action === 'open-add-product') showAddProductDialog();
  else if (action === 'add-search-product') showAddProductDialog(target.dataset.productName);
  else if (action === 'toggle-favorite') toggleFavorite(target.dataset.inventoryId, target.dataset.favorite === 'true');
  else if (action === 'edit-profile') showProfileDialog();
  else if (action === 'show-token') showTokenDialog();
  else if (action === 'copy-token') navigator.clipboard.writeText(state.accessToken).then(() => toast('토큰을 복사했어요.', 'success'));
  else if (action === 'logout') logout();
  else if (action === 'back') {
    state.analysisResult = null;
    state.optimization = null;
    state.analysisStatus = null;
    state.activeView = 'analysis';
    render();
  }

  const skinType = target.dataset.skinType;
  if (skinType) {
    state.onboarding.skinType = skinType;
    render();
  }
  const concern = target.dataset.concern;
  if (concern) {
    const selected = new Set(state.onboarding.skinConcerns);
    if (selected.has(concern)) selected.delete(concern);
    else selected.add(concern);
    state.onboarding.skinConcerns = [...selected];
    render();
  }
  const condition = target.dataset.condition;
  if (condition) {
    state.home ||= structuredClone(demoHome);
    state.home.todayCondition = { ...state.home.todayCondition, logged: true, condition };
    render();
  }
  const inventoryTab = target.dataset.inventoryTab;
  if (inventoryTab) {
    state.inventoryTab = inventoryTab;
    render();
  }
  const analysisId = target.dataset.analysisId;
  if (analysisId) {
    if (target.dataset.analysisStatus === 'COMPLETED') loadAnalysisDetail(analysisId);
    else {
      state.analysisId = Number(analysisId);
      state.analysisStatus = { status: target.dataset.analysisStatus, progress: 0, message: '분석 상태를 확인하고 있어요.' };
      render();
      pollAnalysis();
    }
  }
  if (target.dataset.resultId) showProductDetail({ resultId: target.dataset.resultId });
  else if (target.dataset.inventoryId && action !== 'toggle-favorite') showProductDetail({ inventoryId: target.dataset.inventoryId, productId: target.dataset.productId });
  else if (target.dataset.productId) showProductDetail({ productId: target.dataset.productId });
});

root.addEventListener('change', (event) => {
  if (event.target.matches('[data-routine-year-select]')) {
    state.routine.year = event.target.value || null;
    state.routine.libraryLoaded = false;
    render();
    loadRoutineLibrary().catch((error) => toast(`보관함을 불러오지 못했어요. ${error.message}`, 'error'));
  }
});

root.addEventListener('input', (event) => {
  if (event.target.id === 'onboarding-nickname') {
    state.onboarding.nickname = event.target.value;
    const count = root.querySelector('.character-count');
    const button = root.querySelector('[data-action="complete-onboarding"]');
    if (count) count.textContent = `${[...event.target.value].length}/10`;
    if (button) button.disabled = !event.target.value.trim();
  }
  if (event.target.id === 'video-url') {
    state.videoUrl = event.target.value;
    state.preview = null;
    schedulePreview();
  }
});

root.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target;
  if (form.id === 'condition-form') updateCondition(new FormData(form).get('memo') || '');
  else if (form.id === 'analysis-form') startAnalysis();
  else if (form.id === 'product-search-form') searchProducts(new FormData(form).get('query') || '');
  else if (form.id === 'generated-routine-form') {
    await createGeneratedRoutine(String(new FormData(form).get('name') || ''), event.submitter?.value || 'TODAY');
  }
});

dialog.addEventListener('click', (event) => {
  if (event.target === dialog || event.target.closest('[data-dialog-close]')) closeDialog();
  const tab = event.target.closest('[data-detail-tab]');
  if (tab) {
    dialog.querySelectorAll('[data-detail-tab]').forEach((item) => item.classList.toggle('active', item === tab));
    dialog.querySelectorAll('[data-detail-pane]').forEach((pane) => pane.classList.toggle('active', pane.dataset.detailPane === tab.dataset.detailTab));
  }
  const deleteButton = event.target.closest('[data-action="delete-inventory"]');
  if (deleteButton) deleteInventory(deleteButton.dataset.inventoryId);
  const copyButton = event.target.closest('[data-action="copy-token"]');
  if (copyButton && state.accessToken) navigator.clipboard.writeText(state.accessToken).then(() => toast('토큰을 복사했어요.', 'success'));
  const applyRoutineButton = event.target.closest('[data-action="apply-routine-today"]');
  if (applyRoutineButton) applyArchivedRoutine(applyRoutineButton.dataset.routineId);
  const deleteRoutineButton = event.target.closest('[data-action="delete-routine"]');
  if (deleteRoutineButton) deleteArchivedRoutine(deleteRoutineButton.dataset.routineId);
  const routineRecordButton = event.target.closest('[data-action="open-routine-record"]');
  if (routineRecordButton && state.routine.selectedDateDetail) {
    showRoutineDateDialog(state.routine.selectedDateDetail, routineRecordButton.dataset.routineId);
  }
  const routineRecordBackButton = event.target.closest('[data-action="show-routine-record-list"]');
  if (routineRecordBackButton && state.routine.selectedDateDetail) {
    showRoutineDateDialog(state.routine.selectedDateDetail);
  }
  const reanalysisButton = event.target.closest('[data-action="reanalyze-ingredients"]');
  if (reanalysisButton) reanalyzeIngredients(reanalysisButton.dataset.analysisId);
});

dialog.addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.target;
  const data = new FormData(form);
  if (form.id === 'settings-form') {
    const backendUrl = String(data.get('backendUrl') || '').trim().replace(/\/$/, '');
    const token = String(data.get('accessToken') || '').trim().replace(/^Bearer\s+/i, '');
    state.backendUrl = backendUrl;
    state.accessToken = token;
    state.isDemo = false;
    api.setBaseUrl(backendUrl);
    closeDialog();
    if (token) await hydrateApp();
    else {
      state.activeView = 'splash';
      render();
      toast('백엔드 URL을 저장했어요. 연결하려면 Access Token이 필요합니다.');
    }
  } else if (form.id === 'profile-form') await saveProfile(data);
  else if (form.id === 'add-product-form') await addProduct(String(data.get('productName') || '').trim());
  else if (form.id === 'save-routine-form') {
    const submitter = event.submitter;
    await saveRoutine(submitter?.value || 'TODAY', String(data.get('routineType') || ''));
  }
});

async function initialize() {
  const query = new URLSearchParams(window.location.search);
  const previewOnboarding = query.get('onboarding') === '1';
  const code = query.get('code');
  const returnedState = query.get('state');
  const oauthError = query.get('error');
  const oauthErrorDescription = query.get('error_description');

  state.activeView = 'splash';
  render();

  if (previewOnboarding && !code && !oauthError) {
    state.isDemo = true;
    state.onboardingStep = 0;
    await hydrateApp({ forceOnboarding: true });
    return;
  }

  if (code || oauthError) window.history.replaceState({}, document.title, '/');
  if (oauthError) {
    sessionStorage.removeItem('soak-fe-test-oauth-state');
    toast(`카카오 로그인이 취소되었어요. ${oauthErrorDescription || oauthError}`, 'error');
    return;
  }
  if (!code) return;

  const expectedState = sessionStorage.getItem('soak-fe-test-oauth-state');
  sessionStorage.removeItem('soak-fe-test-oauth-state');
  if (!expectedState || !returnedState || expectedState !== returnedState) {
    toast('OAuth state 값이 일치하지 않습니다. 로그인을 다시 시작해 주세요.', 'error');
    return;
  }
  await exchangeKakaoCode(code);
}

initialize();
