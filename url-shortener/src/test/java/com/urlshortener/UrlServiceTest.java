package com.urlshortener;

import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock UrlMappingRepository urlMappingRepository;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UrlService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "shortCodeLength", 6);
        ReflectionTestUtils.setField(urlService, "defaultTtlDays", 365);
        ReflectionTestUtils.setField(urlService, "cacheTtlSeconds", 3600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void shorten_shouldSaveAndCacheUrl() {
        when(urlMappingRepository.existsByShortCode(any())).thenReturn(false);
        when(urlMappingRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UrlMapping result = urlService.shorten("https://example.com", "user1", null, null);

        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
        assertThat(result.getShortCode()).hasSize(6);
        verify(valueOps).set(anyString(), eq("https://example.com"), anyLong(), any());
    }

    @Test
    void resolve_shouldReturnCachedUrl() {
        when(valueOps.get("url:abc123")).thenReturn("https://example.com");

        String url = urlService.resolve("abc123");

        assertThat(url).isEqualTo("https://example.com");
        verify(urlMappingRepository, never()).findByShortCodeAndActiveTrue(any());
    }

    @Test
    void resolve_shouldFallbackToDb_whenCacheMiss() {
        when(valueOps.get(anyString())).thenReturn(null);
        UrlMapping mapping = UrlMapping.builder()
            .shortCode("abc123")
            .originalUrl("https://example.com")
            .active(true)
            .build();
        when(urlMappingRepository.findByShortCodeAndActiveTrue("abc123"))
            .thenReturn(Optional.of(mapping));

        String url = urlService.resolve("abc123");

        assertThat(url).isEqualTo("https://example.com");
        verify(valueOps).set(anyString(), eq("https://example.com"), anyLong(), any());
    }

    @Test
    void resolve_shouldThrow_whenUrlNotFound() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(urlMappingRepository.findByShortCodeAndActiveTrue("missing"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> urlService.resolve("missing"))
            .isInstanceOf(UrlService.UrlNotFoundException.class);
    }

    @Test
    void shorten_shouldThrow_whenAliasAlreadyTaken() {
        when(urlMappingRepository.findByCustomAlias("myalias"))
            .thenReturn(Optional.of(UrlMapping.builder().build()));

        assertThatThrownBy(() -> urlService.shorten("https://example.com", "user1", "myalias", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already in use");
    }
}
