package com.example.phm.alarm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AlarmFocusAnalysisServiceTest {

    /** 구간 폭 검사는 저장소를 건드리기 전에 끝나므로 협력 객체 없이 검증한다. */
    private final AlarmFocusAnalysisService service =
            new AlarmFocusAnalysisService(null, null, null, null, null);

    @Test
    void rejectsSelectionWiderThanRangeLimit() {
        assertThatThrownBy(() -> service.analyzeSelection(1L, 0L, 120_001L, 64000))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsSelectionWithNonPositiveWidth() {
        assertThatThrownBy(() -> service.analyzeSelection(1L, 1_000L, 1_000L, 64000))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
