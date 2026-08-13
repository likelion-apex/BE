package domain.cosmetic.cache;

import domain.cosmetic.client.CsmtcsReglMaterialClient;
import domain.cosmetic.client.RegulationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 화장품 규제정보는 성분명으로 서버 필터링이 되지 않으므로,
 * 앱 기동 시 전체 데이터를 캐싱해두고 이름으로 로컬 조회한다.
 * 매일 새벽 4시에 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegulationInfoCache {

    private final CsmtcsReglMaterialClient client;
    private final Map<String, RegulationInfo> cache = new ConcurrentHashMap<>();

    // 기동 직후(포트가 이미 열린 뒤) 백그라운드 스레드에서 최초 적재를 시작한다.
    // @PostConstruct로 동기 호출하면 내장 Tomcat이 커넥터를 여는 시점(finishRefresh)보다
    // 앞서 블로킹되어, 전체 페이지네이션(약 14회 외부 호출)이 끝나기 전까지 헬스체크용
    // 포트가 열리지 않는 문제가 있었다.
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        log.info("화장품 규제정보 캐시 최초 적재를 백그라운드에서 시작합니다.");
        Thread.ofVirtual().name("regulation-info-cache-warmup").start(this::refresh);
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void refresh() {
        try {
            List<RegulationInfo> all = client.fetchAll();
            Map<String, RegulationInfo> loaded = new ConcurrentHashMap<>();
            for (RegulationInfo info : all) {
                if (info.standardName() != null && !info.standardName().isBlank()) {
                    loaded.put(normalize(info.standardName()), info);
                }
            }
            cache.clear();
            cache.putAll(loaded);
            log.info("화장품 규제정보 캐시 갱신 완료: {}건", cache.size());
        } catch (Exception e) {
            log.warn("화장품 규제정보 캐시 갱신 실패, 기존 캐시({}건)를 유지합니다.", cache.size(), e);
        }
    }

    public Optional<RegulationInfo> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(normalize(name)));
    }

    private String normalize(String name) {
        return name.trim();
    }
}
