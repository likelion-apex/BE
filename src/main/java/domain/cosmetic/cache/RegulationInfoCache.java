package domain.cosmetic.cache;

import domain.cosmetic.client.CsmtcsReglMaterialClient;
import domain.cosmetic.client.RegulationInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${mfds.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        refresh();
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void refresh() {
        if (!enabled) {
            log.info("로컬 프로필에서는 화장품 규제정보 외부 연동을 건너뜁니다.");
            return;
        }
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
