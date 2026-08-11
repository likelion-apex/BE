package domain.member;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_member_provider_provider_id", columnNames = {"provider", "provider_id"})
})
public class Member {

    public static final int NICKNAME_MAX_LENGTH = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "skin_type", length = 30)
    private SkinType skinType;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_skin_concerns", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "skin_concern", length = 30)
    private Set<SkinConcern> skinConcerns = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public Member(String email, String nickname, String profileImageUrl, Provider provider, String providerId, Role role) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role != null ? role : Role.USER;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateSkinType(SkinType skinType) {
        this.skinType = skinType;
    }

    public void updateSkinConcerns(Set<SkinConcern> skinConcerns) {
        this.skinConcerns.clear();
        if (skinConcerns != null) {
            this.skinConcerns.addAll(skinConcerns);
        }
    }
}
