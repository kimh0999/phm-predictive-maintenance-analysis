<template>
  <div class="dashboard-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"></span>
        <span class="brand-text">{{ t('brand') }}</span>
      </div>
      <div class="search-box">{{ t('search') }}</div>
      <nav class="nav-section">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', activeNav === item.key ? 'active' : '']"
          type="button"
          @click="selectNav(item)"
        >
          {{ t(item.labelKey) }}
        </button>
      </nav>
      <div class="side-foot">
        <span :class="['status-dot', dbStatus.connected ? 'ok' : 'warn']"></span>
        <span>{{ dbStatus.connected ? t('dbConnected') : t('dbOffline') }}</span>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <div class="eyebrow">{{ t('eyebrow') }}</div>
          <h1>{{ activeTitle }}</h1>
        </div>
        <div class="toolbar">
          <select v-model="selectedEquipment" class="easy-select" @change="loadDashboard">
            <option v-for="equipment in equipments" :key="equipment.equipmentCode" :value="equipment.equipmentCode">
              {{ equipment.equipmentCode }} · {{ equipment.equipmentName }}
            </option>
          </select>
          <select v-model="language" class="easy-select lang-select">
            <option value="ko">한국어</option>
            <option value="en">English</option>
          </select>
          <button
            :class="['icon-action', autoRefresh ? 'active' : '']"
            type="button"
            :title="autoRefresh ? t('autoOn') : t('autoOff')"
            :aria-label="autoRefresh ? t('autoOn') : t('autoOff')"
            @click="toggleAutoRefresh"
          >
            <svg v-if="autoRefresh" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 5h3v14H8zM13 5h3v14h-3z"></path>
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 5v14l11-7z"></path>
            </svg>
          </button>
          <button
            :class="['icon-action', isRefreshing ? 'spinning' : '']"
            type="button"
            :disabled="isRefreshing"
            :title="isRefreshing ? t('refreshing') : t('refresh')"
            :aria-label="isRefreshing ? t('refreshing') : t('refresh')"
            @click="loadDashboard"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M19 8a7 7 0 0 0-12.1-3.9L5 6"></path>
              <path d="M5 3v3h3"></path>
              <path d="M5 16a7 7 0 0 0 12.1 3.9L19 18"></path>
              <path d="M19 21v-3h-3"></path>
            </svg>
          </button>
        </div>
      </header>

      <section class="segment-strip">
        <button
          v-for="segment in segments"
          :key="segment.key"
          :class="['segment', activeSegment === segment.key ? 'active' : '']"
          type="button"
          @click="setActiveSegment(segment.key, true)"
        >
          {{ t(segment.labelKey) }}
        </button>
      </section>

      <section class="system-strip">
        <article class="system-card">
          <span class="system-label">{{ t('collectionStatus') }}</span>
          <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">{{ tLevel(latestAnalysis.alarmLevel) }}</strong>
          <small>{{ t('lastUpdated') }} {{ formattedLastUpdated }}</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('windowsStored') }}</span>
          <strong>{{ summary.recentAnalysisCount || analysisResults.length }}</strong>
          <small>
            {{ t('rawWindows') }} {{ rawSeries.windowCount || 0 }} ·
            {{ t('displaySamples') }} {{ rawSeries.sampleCount || 0 }}/{{ rawSeries.originalSampleCount || rawSeries.sampleCount || 0 }}
          </small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('latestEquipment') }}</span>
          <strong>{{ selectedEquipment || '-' }}</strong>
          <small>{{ latestRaw.rpm || '-' }} RPM · {{ latestRaw.samplingRate || '-' }} Hz</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('serverStatus') }}</span>
          <strong>{{ dbStatus.connected ? 'OK' : 'CHECK' }}</strong>
          <small>Spring Boot · FastAPI · MySQL</small>
        </article>
      </section>

      <section class="summary-line">
        <div class="summary-pill">
          <span class="label">{{ t('latestWindow') }}</span>
          <strong>#{{ latestRaw.windowIndex ?? '-' }}</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('rms') }}</span>
          <strong>{{ formatNumber(latestAnalysis.rms, 5) }}</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('peakFrequency') }}</span>
          <strong>{{ formatNumber(latestAnalysis.peakFrequency, 2) }} Hz</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('anomalyScore') }}</span>
          <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">
            {{ formatNumber(latestAnalysis.anomalyScore, 4) }}
          </strong>
        </div>
      </section>

      <section v-show="isSegmentVisible('raw')" class="hero-chart">
        <div class="raw-chart-card">
          <EChartPanel :option="rawSignalOption" />
        </div>
      </section>

      <section v-show="isSegmentVisible('fft')" class="metric-workspace">
        <div class="model-result-strip">
          <article class="model-result-card">
            <span>{{ t('aiPrediction') }}</span>
            <strong>{{ latestAnalysis.prediction || '-' }}</strong>
            <small>{{ t('modelVersion') }} {{ latestAnalysis.modelVersion || '-' }}</small>
          </article>
          <article class="model-result-card">
            <span>{{ t('modelStatus') }}</span>
            <strong>{{ tModelStatus(latestAnalysis.modelStatus) }}</strong>
            <small>{{ t('modelInputType') }} {{ latestAnalysis.modelInputType || '-' }}</small>
          </article>
          <article class="model-result-card">
            <span>{{ t('confidence') }}</span>
            <strong>{{ formatPercent(latestAnalysis.confidence) }}</strong>
            <small>{{ t('modelExpectedInput') }} {{ latestAnalysis.modelExpectedInputSize || '-' }}</small>
          </article>
          <article class="model-result-card">
            <span>{{ t('alarmDecision') }}</span>
            <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">{{ tLevel(latestAnalysis.alarmLevel) }}</strong>
            <small>{{ latestAnalysis.modelInputStrategy || t('modelConfidenceHelp') }}</small>
          </article>
        </div>
        <div class="kpi-grid">
          <article
            v-for="metric in metricCards"
            :key="metric.key"
            :class="['metric-card', selectedMetricKey === metric.key ? 'selected' : '']"
            role="button"
            tabindex="0"
            :aria-label="`${metric.label} ${t('detailAnalysis')}`"
            @click="selectMetric(metric.key)"
            @keydown.enter.prevent="selectMetric(metric.key)"
            @keydown.space.prevent="selectMetric(metric.key)"
          >
            <span
              class="metric-hit-area"
              aria-hidden="true"
              @click.stop="selectMetric(metric.key)"
              @mouseenter="showMetricTooltip(metric.key, $event)"
              @mousemove="moveMetricTooltip($event)"
              @mouseleave="hideMetricTooltip"
            ></span>
            <div class="metric-rank">{{ metric.rank }}</div>
            <span class="metric-open-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <path d="M8 5l8 7-8 7z"></path>
              </svg>
            </span>
            <div class="metric-title">{{ metric.label }}</div>
            <div class="metric-value">{{ metric.value }}</div>
            <div class="mini-chart">
              <EChartPanel :option="metric.option" />
            </div>
          </article>
        </div>
      </section>

      <section v-show="isSegmentVisible('overview')" class="overview-workspace">
        <div class="overview-grid">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('equipmentDistribution') }}</strong>
              </div>
              <em>{{ summary.equipmentCount || equipments.length }} {{ t('equipmentUnit') }}</em>
            </header>
            <EChartPanel :option="statusPieOption" />
          </article>

          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('alarmInsight') }}</span>
                <strong>{{ t('alarmDistribution') }}</strong>
              </div>
              <em>{{ summary.recentAlarmCount || alarms.length }} {{ t('alarmUnit') }}</em>
            </header>
            <EChartPanel :option="alarmPieOption" />
          </article>

          <article class="monitor-panel equipment-health-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('equipmentLatestStatus') }}</strong>
              </div>
              <em>{{ formattedLastUpdated }}</em>
            </header>
            <div class="equipment-health-list">
              <button
                v-for="row in equipmentHealthRows"
                :key="row.equipmentCode"
                :class="['equipment-health-row', selectedEquipment === row.equipmentCode ? 'selected' : '']"
                type="button"
                @click="selectEquipment(row.equipmentCode)"
              >
                <span class="equipment-code">{{ row.equipmentCode }}</span>
                <span class="equipment-name">{{ row.equipmentName }}</span>
                <span :class="['level-badge', row.alarmLevel]">{{ tLevel(row.alarmLevel) }}</span>
                <span class="equipment-reading">{{ t('rms') }} {{ formatNumber(row.rms, 4) }}</span>
                <span class="equipment-reading">{{ t('anomalyScore') }} {{ formatNumber(row.anomalyScore, 3) }}</span>
              </button>
            </div>
          </article>
        </div>

        <div class="overview-grid lower">
          <article class="monitor-panel wide-chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ selectedEquipment }}</span>
                <strong>{{ t('featureTrendTitle') }}</strong>
              </div>
              <em>{{ analysisResults.length }} {{ t('windowUnit') }}</em>
            </header>
            <EChartPanel :option="featureTrendOption" />
          </article>

          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('riskMap') }}</span>
                <strong>{{ t('riskScatterTitle') }}</strong>
              </div>
              <em>{{ t('rms') }} · {{ t('kurtosis') }}</em>
            </header>
            <EChartPanel :option="riskScatterOption" />
          </article>
        </div>

        <div class="overview-grid bottom">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('fleetScoreTitle') }}</strong>
              </div>
              <em>{{ t('anomalyScore') }}</em>
            </header>
            <EChartPanel :option="fleetScoreBarOption" />
          </article>

          <article class="monitor-panel alarm-feed-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('recentAlarms') }}</span>
                <strong>{{ t('latestAlarmFeed') }}</strong>
              </div>
              <em>{{ alarms.length }} {{ t('rowsUnit') }}</em>
            </header>
            <div v-if="actualAlarmRows.length === 0" class="empty-state">
              {{ t('noAlarm') }}
            </div>
            <div v-else class="alarm-feed-list">
              <div v-for="alarm in recentAlarmItems" :key="alarm.id" class="alarm-feed-item">
                <span :class="['level-badge', alarm.rawLevel]">{{ alarm.alarmLevel }}</span>
                <strong>{{ alarm.equipmentCode }}</strong>
                <span>{{ alarm.displayMessage }}</span>
                <time>{{ alarm.occurredAt }}</time>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-show="isSegmentVisible('alarms')" class="alarm-workspace">
        <div class="alarm-summary-grid">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('alarmInsight') }}</span>
                <strong>{{ t('alarmDistribution') }}</strong>
              </div>
              <em>{{ summary.recentAlarmCount || alarms.length }} {{ t('alarmUnit') }}</em>
            </header>
            <EChartPanel :option="alarmPieOption" />
          </article>
          <article class="monitor-panel alarm-count-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('recentAlarms') }}</span>
                <strong>{{ t('alarmSummary') }}</strong>
              </div>
              <em>{{ formattedLastUpdated }}</em>
            </header>
            <div class="alarm-count-grid">
              <div>
                <span>{{ t('danger') }}</span>
                <strong class="alarm-text danger">{{ alarmCounts.danger }}</strong>
              </div>
              <div>
                <span>{{ t('warning') }}</span>
                <strong class="alarm-text warning">{{ alarmCounts.warning }}</strong>
              </div>
              <div>
                <span>{{ t('normal') }}</span>
                <strong class="alarm-text normal">{{ alarmCounts.normal }}</strong>
              </div>
            </div>
          </article>
        </div>

        <article class="monitor-panel alarm-table-panel">
          <header class="panel-heading">
            <div>
              <span>{{ t('recentAlarms') }}</span>
              <strong>{{ t('alarmHistoryTable') }}</strong>
            </div>
            <em>{{ actualAlarmRows.length }} {{ t('rowsUnit') }}</em>
          </header>
          <div v-if="actualAlarmRows.length === 0" class="empty-state">
            {{ t('noAlarm') }}
          </div>
          <div v-else class="alarm-table-wrap">
            <table class="alarm-table">
              <thead>
                <tr>
                  <th>{{ t('occurredAt') }}</th>
                  <th>{{ t('equipment') }}</th>
                  <th>{{ t('level') }}</th>
                  <th>{{ t('evidence') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="alarm in actualAlarmRows" :key="alarm.id">
                  <td>{{ alarm.occurredAt }}</td>
                  <td>{{ alarm.equipmentCode }}</td>
                  <td><span :class="['level-badge', alarm.rawLevel]">{{ alarm.alarmLevel }}</span></td>
                  <td>{{ alarm.displayMessage }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <div
        v-if="metricHover.visible && !showMetricDetail"
        class="metric-hover-tooltip"
        :style="metricHoverStyle"
      >
        <strong>{{ hoverMetricSpec.label }}</strong>
        <span>{{ t('current') }} {{ formatNumber(hoverMetricStats.current, hoverMetricSpec.decimals) }}{{ hoverMetricSpec.unit ? ` ${hoverMetricSpec.unit}` : '' }}</span>
        <span>{{ t('average') }} {{ formatNumber(hoverMetricStats.average, hoverMetricSpec.decimals) }}{{ hoverMetricSpec.unit ? ` ${hoverMetricSpec.unit}` : '' }}</span>
        <span>{{ t('minimum') }} {{ formatNumber(hoverMetricStats.minimum, hoverMetricSpec.decimals) }} / {{ t('maximum') }} {{ formatNumber(hoverMetricStats.maximum, hoverMetricSpec.decimals) }}</span>
      </div>

      <div v-if="showMetricDetail" class="metric-expanded-backdrop" @click.self="closeMetricDetail">
        <section class="metric-expanded-panel" role="dialog" aria-modal="true" :aria-label="metricDetailTitle">
          <header class="metric-expanded-header">
            <div>
              <span class="metric-expanded-kicker">{{ t('fftFeatures') }}</span>
              <h2>{{ metricDetailTitle }}</h2>
              <p>{{ t('expandedHint') }}</p>
            </div>
            <button class="icon-action metric-close-button" type="button" :title="t('close')" :aria-label="t('close')" @click="closeMetricDetail">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12"></path>
                <path d="M18 6L6 18"></path>
              </svg>
            </button>
          </header>
          <div class="metric-stat-strip expanded">
            <div class="metric-stat">
              <span>{{ t('current') }}</span>
              <strong>{{ formatNumber(metricDetailStats.current, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('average') }}</span>
              <strong>{{ formatNumber(metricDetailStats.average, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('minimum') }}</span>
              <strong>{{ formatNumber(metricDetailStats.minimum, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('maximum') }}</span>
              <strong>{{ formatNumber(metricDetailStats.maximum, selectedMetricSpec.decimals) }}</strong>
            </div>
          </div>
          <EChartPanel :option="metricDetailOption" />
        </section>
      </div>
    </main>
  </div>
</template>

<script>
import EChartPanel from '../components/EChartPanel.vue';
import {
  fetchAlarms,
  fetchAnalysisResults,
  fetchDashboardSummary,
  fetchDatabaseStatus,
  fetchEquipments,
  fetchLatestRawWindow,
  fetchRawVibrationSeries
} from '../api/client';

const alarmColor = {
  normal: '#1f9d55',
  warning: '#f6a609',
  danger: '#d64545',
  unknown: '#7a8699'
};

const messages = {
  ko: {
    brand: 'PHM 모니터',
    search: '리포트 및 설비 검색',
    dbConnected: 'MySQL 연결됨',
    dbOffline: 'DB 연결 확인',
    eyebrow: '로컬 스마트팩토리 PHM MVP',
    dashboard: '대시보드',
    realtime: '실시간',
    vibration: '진동 신호',
    fft: 'FFT 분석',
    alarms: '알람 이력',
    overview: '개요',
    raw: '원본 신호',
    fftFeatures: 'FFT 특징',
    alarmTab: '알람',
    overviewTitle: '진동 모니터링 개요',
    rawTitle: '원본 진동 시계열',
    fftTitle: 'FFT 특징 분석',
    alarmsTitle: '알람 이력',
    autoOn: '자동 갱신 ON',
    autoOff: '자동 갱신 OFF',
    refreshing: '갱신 중',
    refresh: '새로고침',
    collectionStatus: '수집 상태',
    lastUpdated: '최종 갱신',
    windowsStored: '최근 분석 Window',
    rawWindows: '원본 Window',
    samples: '샘플',
    displaySamples: '표시 샘플',
    latestEquipment: '선택 설비',
    serverStatus: '서버 상태',
    latestWindow: '최신 Window',
    rms: 'RMS',
    peakFrequency: 'Peak Frequency',
    anomalyScore: '이상 점수',
    aiPrediction: 'AI 예측',
    confidence: '신뢰도',
    modelVersion: '모델',
    modelStatus: '모델 상태',
    modelInputType: '입력',
    modelExpectedInput: '기대 입력',
    modelLoaded: '로드됨',
    modelMissing: '모델 없음',
    modelError: '오류',
    modelUnavailable: '미사용',
    alarmDecision: '알람 판정',
    modelConfidenceHelp: '모델 predict_proba 기준',
    peakToPeak: 'Peak-to-Peak',
    crestFactor: 'Crest Factor',
    kurtosis: 'Kurtosis',
    rawSeriesTitle: '누적 원본 진동 시계열',
    featureTrendTitle: 'FFT/특징값 추세',
    sampleIndex: '샘플',
    equipmentDistribution: '설비 상태 분포',
    riskScatterTitle: 'RMS-Kurtosis 위험 공간',
    alarmDistribution: '알람 단계 분포',
    recentAlarms: '최근 알람 이력',
    fleetStatus: '전체 설비',
    alarmInsight: '알람 분석',
    equipmentLatestStatus: '설비별 최신 상태',
    latestAlarmFeed: '최근 알람 피드',
    alarmSummary: '알람 요약',
    alarmHistoryTable: '알람 이력 테이블',
    fleetScoreTitle: '설비별 이상 점수',
    riskMap: '위험 맵',
    equipmentUnit: '대',
    alarmUnit: '건',
    windowUnit: 'window',
    rowsUnit: 'rows',
    occurredAt: '발생 시각',
    equipment: '설비',
    level: '단계',
    message: '메시지',
    evidence: '판단 근거',
    normal: '정상',
    warning: '주의',
    danger: '위험',
    unknown: '미확인',
    noAlarm: '현재 warning/danger 알람 이력이 없습니다.',
    rawSeries: '원본 진동',
    anomaly: '이상 점수',
    faultSpace: '위험 공간',
    detailAnalysis: '상세 분석',
    current: '현재값',
    average: '평균',
    minimum: '최소',
    maximum: '최대',
    normalRange: '정상',
    attentionRange: '관찰',
    cautionRange: '주의',
    warningRange: '경고',
    dangerRange: '위험',
    metricDetailHelp: '카드를 선택하면 해당 지표를 확대 분석합니다.',
    expandedHint: '마우스 휠/드래그로 구간을 확대하고, 우측 도구로 복원하거나 이미지로 저장할 수 있습니다.',
    close: '닫기'
  },
  en: {
    brand: 'PHM Monitor',
    search: 'Find reports & assets',
    dbConnected: 'MySQL Connected',
    dbOffline: 'DB Offline',
    eyebrow: 'Local Smart Factory PHM MVP',
    dashboard: 'Dashboard',
    realtime: 'Realtime',
    vibration: 'Vibration',
    fft: 'FFT Analysis',
    alarms: 'Alarm History',
    overview: 'Overview',
    raw: 'Raw Signal',
    fftFeatures: 'FFT Features',
    alarmTab: 'Alarms',
    overviewTitle: 'Vibration Monitoring Overview',
    rawTitle: 'Raw Signal Replay',
    fftTitle: 'FFT Feature Analysis',
    alarmsTitle: 'Alarm History',
    autoOn: 'Auto Refresh ON',
    autoOff: 'Auto Refresh OFF',
    refreshing: 'Refreshing',
    refresh: 'Refresh',
    collectionStatus: 'Collection Status',
    lastUpdated: 'Last updated',
    windowsStored: 'Recent Analysis Windows',
    rawWindows: 'Raw Windows',
    samples: 'Samples',
    displaySamples: 'Display Samples',
    latestEquipment: 'Selected Equipment',
    serverStatus: 'Server Status',
    latestWindow: 'Latest Window',
    rms: 'RMS',
    peakFrequency: 'Peak Frequency',
    anomalyScore: 'Anomaly Score',
    aiPrediction: 'AI Prediction',
    confidence: 'Confidence',
    modelVersion: 'Model',
    modelStatus: 'Model Status',
    modelInputType: 'Input',
    modelExpectedInput: 'Expected Input',
    modelLoaded: 'Loaded',
    modelMissing: 'Missing',
    modelError: 'Error',
    modelUnavailable: 'Unavailable',
    alarmDecision: 'Alarm Decision',
    modelConfidenceHelp: 'Based on model predict_proba',
    peakToPeak: 'Peak-to-Peak',
    crestFactor: 'Crest Factor',
    kurtosis: 'Kurtosis',
    rawSeriesTitle: 'Accumulated Raw Vibration Time Series',
    featureTrendTitle: 'FFT / Feature Trend',
    sampleIndex: 'Sample',
    equipmentDistribution: 'Equipment Status Distribution',
    riskScatterTitle: 'RMS vs Kurtosis Risk Space',
    alarmDistribution: 'Alarm Level Distribution',
    recentAlarms: 'Recent Alarm History',
    fleetStatus: 'Fleet',
    alarmInsight: 'Alarm Insight',
    equipmentLatestStatus: 'Latest Equipment Status',
    latestAlarmFeed: 'Latest Alarm Feed',
    alarmSummary: 'Alarm Summary',
    alarmHistoryTable: 'Alarm History Table',
    fleetScoreTitle: 'Anomaly Score by Equipment',
    riskMap: 'Risk Map',
    equipmentUnit: 'assets',
    alarmUnit: 'alarms',
    windowUnit: 'windows',
    rowsUnit: 'rows',
    occurredAt: 'Occurred At',
    equipment: 'Equipment',
    level: 'Level',
    message: 'Message',
    evidence: 'Evidence',
    normal: 'normal',
    warning: 'warning',
    danger: 'danger',
    unknown: 'unknown',
    noAlarm: 'No warning or danger alarm has been recorded.',
    rawSeries: 'Raw Vibration',
    anomaly: 'Anomaly Score',
    faultSpace: 'Fault Space',
    detailAnalysis: 'Detail Analysis',
    current: 'Current',
    average: 'Average',
    minimum: 'Min',
    maximum: 'Max',
    normalRange: 'Normal',
    attentionRange: 'Observe',
    cautionRange: 'Caution',
    warningRange: 'Warning',
    dangerRange: 'Danger',
    metricDetailHelp: 'Select a card to inspect the metric in detail.',
    expandedHint: 'Use wheel or drag to zoom, then use the right-side tools to restore or save the chart.',
    close: 'Close'
  }
};

export default {
  name: 'DashboardView',
  components: {
    EChartPanel
  },
  data() {
    return {
      language: 'ko',
      navItems: [
        { key: 'dashboard', labelKey: 'dashboard', segment: 'overview' },
        { key: 'vibration', labelKey: 'vibration', segment: 'raw' },
        { key: 'fft', labelKey: 'fft', segment: 'fft' },
        { key: 'alarms', labelKey: 'alarms', segment: 'alarms' }
      ],
      segments: [
        { key: 'overview', labelKey: 'overview' },
        { key: 'raw', labelKey: 'raw' },
        { key: 'fft', labelKey: 'fftFeatures' },
        { key: 'alarms', labelKey: 'alarmTab' }
      ],
      activeNav: 'dashboard',
      activeSegment: 'overview',
      selectedMetricKey: 'rms',
      showMetricDetail: false,
      autoRefresh: true,
      isRefreshing: false,
      lastUpdatedAt: null,
      dbStatus: { connected: false },
      equipments: [],
      selectedEquipment: 'MOTOR_001',
      latestRaw: {},
      rawSeries: { points: [], sampleCount: 0, originalSampleCount: 0, downsampled: false },
      analysisResults: [],
      equipmentLatestMap: {},
      alarms: [],
      summary: {},
      metricHover: {
        visible: false,
        key: 'rms',
        x: 0,
        y: 0
      },
      refreshTimer: null,
      refreshIntervalMs: 3000,
      rawSeriesWindowLimit: 5,
      rawSeriesMaxPoints: 8000,
      analysisResultLimit: 180,
      alarmLimit: 50
    };
  },
  computed: {
    activeTitle() {
      const titleMap = {
        overview: 'overviewTitle',
        raw: 'rawTitle',
        fft: 'fftTitle',
        alarms: 'alarmsTitle'
      };
      return this.t(titleMap[this.activeSegment] || 'overviewTitle');
    },
    formattedLastUpdated() {
      if (!this.lastUpdatedAt) {
        return '-';
      }
      return this.lastUpdatedAt.toLocaleTimeString();
    },
    latestAnalysis() {
      return this.analysisResults[0] || {};
    },
    ascendingAnalysis() {
      return [...this.analysisResults].reverse();
    },
    currentReplayAnalysis() {
      const rows = [...this.analysisResults]
        .filter((row) => row.createdAt || row.measuredAt)
        .sort((left, right) => this.toTime(right.createdAt || right.measuredAt) - this.toTime(left.createdAt || left.measuredAt));

      if (rows.length === 0) {
        return [];
      }

      const currentRows = [];
      let previousReceivedAt = null;
      rows.forEach((row) => {
        if (previousReceivedAt === null) {
          currentRows.push(row);
          previousReceivedAt = this.toTime(row.createdAt || row.measuredAt);
          return;
        }

        const receivedAt = this.toTime(row.createdAt || row.measuredAt);
        if (previousReceivedAt - receivedAt <= 30000) {
          currentRows.push(row);
          previousReceivedAt = receivedAt;
        }
      });

      return currentRows.sort((left, right) => this.toTime(left.measuredAt || left.createdAt) - this.toTime(right.measuredAt || right.createdAt));
    },
    metricSpecs() {
      return {
        rms: { label: this.t('rms'), decimals: 5, unit: '', thresholdMode: 'data' },
        peakFrequency: { label: this.t('peakFrequency'), decimals: 2, unit: 'Hz', thresholdMode: 'data' },
        peakToPeak: { label: this.t('peakToPeak'), decimals: 5, unit: '', thresholdMode: 'data' },
        crestFactor: { label: this.t('crestFactor'), decimals: 3, unit: '', thresholdMode: 'fixed' },
        kurtosis: { label: this.t('kurtosis'), decimals: 3, unit: '', thresholdMode: 'fixed' },
        anomalyScore: { label: this.t('anomalyScore'), decimals: 4, unit: '', thresholdMode: 'fixed' }
      };
    },
    selectedMetricSpec() {
      return this.metricSpecs[this.selectedMetricKey] || this.metricSpecs.rms;
    },
    hoverMetricSpec() {
      return this.metricSpecs[this.metricHover.key] || this.metricSpecs.rms;
    },
    hoverMetricStats() {
      return this.statsForMetric(this.metricHover.key);
    },
    metricHoverStyle() {
      const width = 250;
      const height = 122;
      const viewportWidth = window.innerWidth || 1280;
      const viewportHeight = window.innerHeight || 720;
      const left = Math.min(this.metricHover.x + 16, viewportWidth - width - 12);
      const top = Math.min(this.metricHover.y + 16, viewportHeight - height - 12);
      return {
        left: `${Math.max(12, left)}px`,
        top: `${Math.max(12, top)}px`
      };
    },
    metricDetailTitle() {
      return `${this.selectedMetricSpec.label} ${this.t('detailAnalysis')}`;
    },
    metricDetailStats() {
      return this.statsForMetric(this.selectedMetricKey, this.currentReplayAnalysis);
    },
    metricDetailOption() {
      const rows = this.currentReplayAnalysis.length > 0 ? this.currentReplayAnalysis : this.ascendingAnalysis;
      const data = rows
        .filter((row) => row[this.selectedMetricKey] !== null && row[this.selectedMetricKey] !== undefined)
        .map((row) => [this.toTime(row.measuredAt || row.createdAt), row[this.selectedMetricKey]]);
      const values = data.map((item) => item[1]);
      const pieces = this.metricVisualPieces(this.selectedMetricKey, values);
      const markLines = pieces
        .filter((piece) => piece.lte !== undefined)
        .map((piece) => ({ yAxis: piece.lte }));

      return {
        title: {
          text: this.selectedMetricSpec.label,
          left: '1%',
          textStyle: {
            color: '#3f454f',
            fontSize: 20,
            fontWeight: 700
          },
          subtext: this.t('metricDetailHelp')
        },
        tooltip: {
          trigger: 'axis',
          confine: true,
          axisPointer: {
            type: 'cross'
          },
          position: (pt, params, dom, rect, size) => {
            const x = Math.min(pt[0] + 14, size.viewSize[0] - size.contentSize[0] - 12);
            const y = Math.min(pt[1] + 14, size.viewSize[1] - size.contentSize[1] - 12);
            return [Math.max(12, x), Math.max(12, y)];
          },
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const point = params[0];
            const value = point.value[1];
            const time = new Date(point.value[0]).toLocaleString();
            const unit = this.selectedMetricSpec.unit ? ` ${this.selectedMetricSpec.unit}` : '';
            return [
              `<strong>${this.selectedMetricSpec.label}</strong>`,
              `${time}`,
              `${this.formatNumber(value, this.selectedMetricSpec.decimals)}${unit}`
            ].join('<br/>');
          }
        },
        grid: {
          left: '5%',
          right: '15%',
          bottom: '14%',
          top: 76
        },
        xAxis: {
          type: 'time',
          boundaryGap: false,
          min: data[0] ? data[0][0] : undefined,
          max: data.length > 0 ? data[data.length - 1][0] : undefined
        },
        yAxis: {
          type: 'value',
          scale: true
        },
        toolbox: {
          right: 10,
          feature: {
            dataZoom: {
              yAxisIndex: 'none'
            },
            restore: {},
            saveAsImage: {}
          }
        },
        dataZoom: [
          {
            start: 0,
            end: 100,
            height: 26
          },
          {
            type: 'inside'
          }
        ],
        visualMap: {
          top: 50,
          right: 10,
          dimension: 1,
          pieces,
          outOfRange: {
            color: '#999'
          }
        },
        series: {
          name: this.selectedMetricSpec.label,
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 5,
          data,
          markLine: {
            silent: true,
            lineStyle: {
              color: '#333',
              type: 'dashed'
            },
            data: markLines
          }
        }
      };
    },
    rawSignalOption() {
      const data = this.buildRawSignalData();
      return {
        tooltip: {
          trigger: 'axis',
          position: (pt) => [pt[0], '10%'],
          valueFormatter: (value) => Number(value).toFixed(5)
        },
        title: {
          left: 'center',
          text: this.t('rawSeriesTitle'),
          textStyle: {
            color: '#111827',
            fontSize: 20,
            fontWeight: 700
          }
        },
        toolbox: {
          right: 18,
          top: 10,
          feature: {
            dataZoom: { yAxisIndex: 'none' },
            restore: {},
            saveAsImage: {}
          }
        },
        grid: { top: 72, left: 58, right: 34, bottom: 72 },
        xAxis: {
          type: 'value',
          name: this.t('sampleIndex'),
          boundaryGap: false
        },
        yAxis: {
          type: 'value',
          boundaryGap: [0, '100%']
        },
        dataZoom: [
          {
            type: 'inside',
            start: 0,
            end: 20
          },
          {
            start: 0,
            end: 20,
            height: 28
          }
        ],
        series: [
          {
            name: this.t('rawSeries'),
            type: 'line',
            smooth: true,
            symbol: 'none',
            sampling: 'lttb',
            areaStyle: {
              color: 'rgba(0, 141, 213, 0.2)'
            },
            lineStyle: {
              color: '#008dd5',
              width: 1.8
            },
            data
          }
        ]
      };
    },
    featureTrendOption() {
      const rows = this.ascendingAnalysis;
      return {
        tooltip: { trigger: 'axis' },
        legend: {
          top: 4,
          data: [this.t('rms'), this.t('anomalyScore'), this.t('peakFrequency')]
        },
        grid: { left: 54, right: 70, bottom: 48, top: 48 },
        toolbox: {
          right: 10,
          feature: {
            dataZoom: { yAxisIndex: 'none' },
            restore: {},
            saveAsImage: {}
          }
        },
        dataZoom: [{ start: 0, end: 100 }, { type: 'inside' }],
        visualMap: {
          top: 54,
          right: 8,
          dimension: 1,
          pieces: [
            { gt: 0, lte: 0.45, color: '#1f9d55' },
            { gt: 0.45, lte: 0.7, color: '#f6a609' },
            { gt: 0.7, color: '#d64545' }
          ],
          outOfRange: { color: '#7a8699' }
        },
        xAxis: {
          type: 'time',
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#b8c2cc' } }
        },
        yAxis: [
          { type: 'value', name: 'RMS / Score' },
          { type: 'value', name: 'Hz' }
        ],
        series: [
          {
            name: this.t('rms'),
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 5,
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.rms]),
            markLine: {
              silent: true,
              lineStyle: { color: '#a6b1c2' },
              data: [{ yAxis: 0.45 }, { yAxis: 0.7 }]
            }
          },
          {
            name: this.t('anomalyScore'),
            type: 'line',
            smooth: true,
            symbol: 'none',
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.anomalyScore])
          },
          {
            name: this.t('peakFrequency'),
            type: 'line',
            smooth: true,
            symbol: 'none',
            yAxisIndex: 1,
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.peakFrequency]),
            lineStyle: { color: '#6254a8' }
          }
        ]
      };
    },
    statusPieOption() {
      const rows = this.summary.equipmentStatusDistribution || this.buildStatusDistribution();
      return this.buildDonutOption(this.t('equipmentDistribution'), rows);
    },
    alarmPieOption() {
      const rows = this.summary.alarmLevelDistribution || this.buildAlarmDistribution();
      return this.buildDonutOption(this.t('alarmDistribution'), rows);
    },
    fleetScoreBarOption() {
      const rows = this.equipmentHealthRows;
      return {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const item = params[0];
            const row = item.data.raw;
            return [
              `<strong>${row.equipmentCode}</strong>`,
              `${this.t('level')}: ${this.tLevel(row.alarmLevel)}`,
              `${this.t('anomalyScore')}: ${this.formatNumber(row.anomalyScore, 4)}`,
              `${this.t('rms')}: ${this.formatNumber(row.rms, 5)}`
            ].join('<br/>');
          }
        },
        grid: { left: 46, right: 24, top: 24, bottom: 42 },
        xAxis: {
          type: 'category',
          data: rows.map((row) => row.equipmentCode),
          axisLabel: { color: '#445064' }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 1,
          axisLabel: { color: '#445064' }
        },
        series: [
          {
            name: this.t('anomalyScore'),
            type: 'bar',
            barMaxWidth: 34,
            data: rows.map((row) => ({
              value: Number(row.anomalyScore || 0),
              raw: row,
              itemStyle: { color: alarmColor[row.alarmLevel] || alarmColor.unknown }
            })),
            markLine: {
              silent: true,
              lineStyle: { color: '#a6b1c2', type: 'dashed' },
              data: [{ yAxis: 0.45 }, { yAxis: 0.7 }]
            }
          }
        ]
      };
    },
    riskScatterOption() {
      const rows = this.ascendingAnalysis;
      return {
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            const row = params.data.raw;
            return `${row.equipmentCode}<br/>RMS: ${row.rms}<br/>Kurtosis: ${row.kurtosis}<br/>Score: ${row.anomalyScore}`;
          }
        },
        grid: { left: 48, right: 24, bottom: 42, top: 24 },
        xAxis: { name: 'RMS', type: 'value' },
        yAxis: { name: 'Kurtosis', type: 'value' },
        series: [
          {
            name: this.t('faultSpace'),
            type: 'scatter',
            symbolSize: (value) => Math.max(8, Math.min(26, value[2] * 30)),
            data: rows.map((row) => ({
              value: [row.rms || 0, row.kurtosis || 0, row.anomalyScore || 0],
              raw: row,
              itemStyle: { color: alarmColor[row.alarmLevel || 'normal'] || alarmColor.unknown }
            }))
          }
        ]
      };
    },
    metricCards() {
      return [
        this.metricCard('1', this.t('rms'), 'rms', 5),
        this.metricCard('2', this.t('peakFrequency'), 'peakFrequency', 2, 'Hz'),
        this.metricCard('3', this.t('peakToPeak'), 'peakToPeak', 5),
        this.metricCard('4', this.t('crestFactor'), 'crestFactor', 3),
        this.metricCard('5', this.t('kurtosis'), 'kurtosis', 3),
        this.metricCard('6', this.t('anomalyScore'), 'anomalyScore', 4)
      ];
    },
    actualAlarmRows() {
      return this.alarms.map((alarm) => ({
        ...alarm,
        rawLevel: alarm.alarmLevel || 'normal',
        alarmLevel: this.tLevel(alarm.alarmLevel),
        occurredAt: this.formatDateTime(alarm.occurredAt),
        displayMessage: this.buildAlarmDisplayMessage(alarm)
      }));
    },
    recentAlarmItems() {
      return this.actualAlarmRows.slice(0, 8);
    },
    alarmCounts() {
      const counts = { normal: 0, warning: 0, danger: 0 };
      const rows = this.summary.alarmLevelDistribution || [];
      if (rows.length > 0) {
        rows.forEach((row) => {
          counts[row.name] = Number(row.value || 0);
        });
      } else {
        this.alarms.forEach((alarm) => {
          const level = alarm.alarmLevel || 'normal';
          counts[level] = (counts[level] || 0) + 1;
        });
      }
      return counts;
    },
    equipmentHealthRows() {
      return this.equipments.map((equipment) => {
        const latest = this.equipmentLatestMap[equipment.equipmentCode] || {};
        const fallback = equipment.equipmentCode === this.selectedEquipment ? this.latestAnalysis : {};
        const analysis = latest.id ? latest : fallback;
        return {
          equipmentCode: equipment.equipmentCode,
          equipmentName: equipment.equipmentName,
          location: equipment.location,
          alarmLevel: analysis.alarmLevel || 'normal',
          rms: analysis.rms,
          peakFrequency: analysis.peakFrequency,
          anomalyScore: analysis.anomalyScore,
          updatedAt: analysis.createdAt || analysis.measuredAt
        };
      });
    }
  },
  async mounted() {
    await this.loadDashboard();
    this.startRefreshTimer();
  },
  beforeDestroy() {
    this.stopRefreshTimer();
  },
  methods: {
    t(key) {
      return messages[this.language][key] || messages.ko[key] || key;
    },
    tLevel(level) {
      return this.t(level || 'normal');
    },
    tModelStatus(status) {
      const statusKey = {
        loaded: 'modelLoaded',
        missing: 'modelMissing',
        error: 'modelError',
        unavailable: 'modelUnavailable'
      }[status || 'unavailable'];
      return this.t(statusKey || 'modelUnavailable');
    },
    async loadDashboard() {
      if (this.isRefreshing) {
        return;
      }
      this.isRefreshing = true;
      try {
        const [status, equipments, summary] = await Promise.all([
          fetchDatabaseStatus(),
          fetchEquipments(),
          fetchDashboardSummary()
        ]);
        this.dbStatus = status;
        this.equipments = equipments;
        this.summary = summary;

        if (!this.selectedEquipment && equipments.length > 0) {
          this.selectedEquipment = equipments[0].equipmentCode;
        }

        const equipmentCode = this.selectedEquipment || 'MOTOR_001';
        const equipmentLatestPromise = Promise.all(
          equipments.map(async (equipment) => {
            try {
              const rows = await fetchAnalysisResults(equipment.equipmentCode, 1);
              return [equipment.equipmentCode, rows[0] || {}];
            } catch (error) {
              return [equipment.equipmentCode, {}];
            }
          })
        );
        const [rawWindow, rawSeries, analysisResults, alarms, equipmentLatestEntries] = await Promise.all([
          fetchLatestRawWindow(equipmentCode),
          fetchRawVibrationSeries(equipmentCode, this.rawSeriesWindowLimit, this.rawSeriesMaxPoints),
          fetchAnalysisResults(equipmentCode, this.analysisResultLimit),
          fetchAlarms(this.alarmLimit),
          equipmentLatestPromise
        ]);

        this.latestRaw = rawWindow;
        this.rawSeries = rawSeries;
        this.analysisResults = analysisResults;
        this.alarms = alarms;
        this.equipmentLatestMap = Object.fromEntries(equipmentLatestEntries);
        this.lastUpdatedAt = new Date();
      } catch (error) {
        console.warn('Failed to load dashboard data', error);
      } finally {
        this.isRefreshing = false;
        this.resizeChartsSoon();
      }
    },
    toggleAutoRefresh() {
      this.autoRefresh = !this.autoRefresh;
      if (this.autoRefresh) {
        this.startRefreshTimer();
      } else {
        this.stopRefreshTimer();
      }
    },
    startRefreshTimer() {
      this.stopRefreshTimer();
      this.refreshTimer = window.setInterval(() => {
        if (this.autoRefresh) {
          this.loadDashboard();
        }
      }, this.refreshIntervalMs);
    },
    stopRefreshTimer() {
      if (this.refreshTimer) {
        window.clearInterval(this.refreshTimer);
        this.refreshTimer = null;
      }
    },
    showMetricTooltip(metricKey, event) {
      this.metricHover = {
        visible: true,
        key: metricKey,
        x: event.clientX,
        y: event.clientY
      };
    },
    moveMetricTooltip(event) {
      if (!this.metricHover.visible) {
        return;
      }
      this.metricHover = {
        ...this.metricHover,
        x: event.clientX,
        y: event.clientY
      };
    },
    hideMetricTooltip() {
      this.metricHover = {
        ...this.metricHover,
        visible: false
      };
    },
    selectNav(item) {
      this.activeNav = item.key;
      this.setActiveSegment(item.segment, false);
    },
    selectEquipment(equipmentCode) {
      this.selectedEquipment = equipmentCode;
      this.loadDashboard();
    },
    selectMetric(metricKey) {
      this.selectedMetricKey = metricKey;
      this.showMetricDetail = true;
      this.hideMetricTooltip();
      this.activeSegment = 'fft';
      this.activeNav = 'fft';
      this.resizeChartsSoon();
      this.$nextTick(() => {
        window.setTimeout(() => {
          window.dispatchEvent(new Event('resize'));
        }, 50);
      });
    },
    closeMetricDetail() {
      this.showMetricDetail = false;
      this.resizeChartsSoon();
    },
    setActiveSegment(segment, syncNav) {
      this.activeSegment = segment;
      const matchingNav = this.navItems.find((item) => item.segment === segment);
      if (syncNav && matchingNav) {
        this.activeNav = matchingNav.key;
      }
      this.resizeChartsSoon();
    },
    isSegmentVisible(segment) {
      return this.activeSegment === segment;
    },
    resizeChartsSoon() {
      this.$nextTick(() => {
        window.setTimeout(() => {
          window.dispatchEvent(new Event('resize'));
        }, 0);
      });
    },
    metricValues(metricKey) {
      return this.ascendingAnalysis
        .map((row) => row[metricKey])
        .filter((value) => value !== null && value !== undefined)
        .map(Number)
        .filter((value) => Number.isFinite(value));
    },
    statsForMetric(metricKey, rows = this.ascendingAnalysis) {
      const values = rows
        .map((row) => row[metricKey])
        .filter((value) => value !== null && value !== undefined)
        .map(Number)
        .filter((value) => Number.isFinite(value));
      if (values.length === 0) {
        return { current: null, average: null, minimum: null, maximum: null };
      }
      const sum = values.reduce((total, value) => total + value, 0);
      return {
        current: values[values.length - 1],
        average: sum / values.length,
        minimum: Math.min(...values),
        maximum: Math.max(...values)
      };
    },
    metricVisualPieces(metricKey, values) {
      const colorSet = ['#93CE07', '#FBDB0F', '#FC7D02', '#FD0100', '#AA069F', '#AC3B2A'];

      if (metricKey === 'anomalyScore') {
        return [
          { gt: 0, lte: 0.25, color: colorSet[0], label: `${this.t('normalRange')} <= 0.25` },
          { gt: 0.25, lte: 0.45, color: colorSet[1], label: `0.25 - 0.45` },
          { gt: 0.45, lte: 0.6, color: colorSet[2], label: `0.45 - 0.60` },
          { gt: 0.6, lte: 0.7, color: colorSet[3], label: `0.60 - 0.70` },
          { gt: 0.7, lte: 0.85, color: colorSet[4], label: `0.70 - 0.85` },
          { gt: 0.85, color: colorSet[5], label: `> 0.85` }
        ];
      }

      if (metricKey === 'crestFactor') {
        return [
          { gt: 0, lte: 3, color: colorSet[0], label: `${this.t('normalRange')} <= 3` },
          { gt: 3, lte: 4, color: colorSet[1], label: `3 - 4` },
          { gt: 4, lte: 5, color: colorSet[2], label: `4 - 5` },
          { gt: 5, lte: 6, color: colorSet[3], label: `5 - 6` },
          { gt: 6, lte: 8, color: colorSet[4], label: `6 - 8` },
          { gt: 8, color: colorSet[5], label: `> 8` }
        ];
      }

      if (metricKey === 'kurtosis') {
        return [
          { gt: 0, lte: 3.5, color: colorSet[0], label: `${this.t('normalRange')} <= 3.5` },
          { gt: 3.5, lte: 4.5, color: colorSet[1], label: `3.5 - 4.5` },
          { gt: 4.5, lte: 6, color: colorSet[2], label: `4.5 - 6` },
          { gt: 6, lte: 8, color: colorSet[3], label: `6 - 8` },
          { gt: 8, lte: 10, color: colorSet[4], label: `8 - 10` },
          { gt: 10, color: colorSet[5], label: `> 10` }
        ];
      }

      return this.dataDrivenPieces(values, colorSet);
    },
    dataDrivenPieces(values, colors) {
      if (values.length === 0) {
        return [{ gt: 0, color: colors[0], label: this.t('normalRange') }];
      }

      const sorted = [...values].sort((a, b) => a - b);
      const min = sorted[0];
      const max = sorted[sorted.length - 1];

      if (min === max) {
        const step = Math.max(Math.abs(min) * 0.05, 0.0001);
        return [
          { lte: min - step, color: colors[0], label: `< ${this.formatNumber(min - step, this.selectedMetricSpec.decimals)}` },
          { gt: min - step, lte: min + step, color: colors[1], label: `${this.t('normalRange')}` },
          { gt: min + step, color: colors[3], label: `> ${this.formatNumber(min + step, this.selectedMetricSpec.decimals)}` }
        ];
      }

      const p20 = this.percentile(sorted, 0.2);
      const p40 = this.percentile(sorted, 0.4);
      const p60 = this.percentile(sorted, 0.6);
      const p80 = this.percentile(sorted, 0.8);
      const p95 = this.percentile(sorted, 0.95);

      return [
        { lte: p20, color: colors[0], label: `<= ${this.formatNumber(p20, this.selectedMetricSpec.decimals)}` },
        { gt: p20, lte: p40, color: colors[1], label: `${this.formatNumber(p20, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p40, this.selectedMetricSpec.decimals)}` },
        { gt: p40, lte: p60, color: colors[2], label: `${this.formatNumber(p40, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p60, this.selectedMetricSpec.decimals)}` },
        { gt: p60, lte: p80, color: colors[3], label: `${this.formatNumber(p60, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p80, this.selectedMetricSpec.decimals)}` },
        { gt: p80, lte: p95, color: colors[4], label: `${this.formatNumber(p80, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p95, this.selectedMetricSpec.decimals)}` },
        { gt: p95, color: colors[5], label: `> ${this.formatNumber(p95, this.selectedMetricSpec.decimals)}` }
      ];
    },
    percentile(sortedValues, ratio) {
      if (sortedValues.length === 1) {
        return sortedValues[0];
      }
      const index = (sortedValues.length - 1) * ratio;
      const lower = Math.floor(index);
      const upper = Math.ceil(index);
      if (lower === upper) {
        return sortedValues[lower];
      }
      const weight = index - lower;
      return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight;
    },
    buildRawSignalData() {
      if (this.rawSeries.points && this.rawSeries.points.length > 0) {
        return this.rawSeries.points.map((point, index) => [index, point.value]);
      }

      const values = this.latestRaw.values || [];
      return values.map((value, index) => [index, value]);
    },
    metricCard(rank, label, key, decimals, unit = '') {
      const value = this.latestAnalysis[key];
      const series = this.ascendingAnalysis.map((row, index) => [index, row[key] || 0]);
      return {
        rank,
        key,
        label,
        value: value === undefined || value === null ? '-' : `${Number(value).toFixed(decimals)}${unit ? ` ${unit}` : ''}`,
        option: this.sparklineOption(series)
      };
    },
    sparklineOption(data) {
      return {
        grid: { top: 4, left: 0, right: 0, bottom: 2 },
        xAxis: { type: 'value', show: false },
        yAxis: { type: 'value', show: false },
        series: [
          {
            type: 'line',
            smooth: true,
            symbol: 'none',
            areaStyle: { color: 'rgba(0,141,213,0.12)' },
            lineStyle: { color: '#008dd5', width: 1.2 },
            data
          }
        ]
      };
    },
    buildDonutOption(title, rows) {
      const normalizedRows = rows && rows.length > 0 ? rows : [{ name: 'unknown', value: 0 }];
      const total = normalizedRows.reduce((sum, row) => sum + Number(row.value || 0), 0);
      const chartRows = total > 0 ? normalizedRows : [{ name: 'unknown', value: 1, color: alarmColor.unknown }];
      return {
        title: {
          text: `${total}`,
          subtext: title,
          left: 'center',
          top: '43%',
          textStyle: {
            color: '#111827',
            fontSize: 24,
            fontWeight: 700
          },
          subtextStyle: {
            color: '#6b7280',
            fontSize: 11
          }
        },
        tooltip: {
          trigger: 'item',
          formatter: (params) => `${this.tLevel(params.name)}: ${params.value} (${params.percent}%)`
        },
        legend: {
          bottom: 4,
          left: 'center',
          formatter: (name) => this.tLevel(name)
        },
        series: [
          {
            name: title,
            type: 'pie',
            radius: ['42%', '70%'],
            center: ['50%', '48%'],
            avoidLabelOverlap: false,
            minAngle: 4,
            itemStyle: {
              borderRadius: 8,
              borderColor: '#fff',
              borderWidth: 2
            },
            label: { show: false, position: 'center' },
            emphasis: {
              label: {
                show: true,
                fontSize: 22,
                fontWeight: 'bold',
                formatter: (params) => this.tLevel(params.name)
              }
            },
            labelLine: { show: false },
            data: chartRows.map((row) => ({
              name: row.name,
              value: row.value,
              itemStyle: { color: alarmColor[row.name] || row.color || '#008dd5' }
            }))
          }
        ]
      };
    },
    buildAlarmDistribution() {
      const counts = { normal: 0, warning: 0, danger: 0 };
      this.analysisResults.forEach((row) => {
        counts[row.alarmLevel || 'normal'] = (counts[row.alarmLevel || 'normal'] || 0) + 1;
      });
      return Object.keys(counts).map((name) => ({ name, value: counts[name] }));
    },
    buildStatusDistribution() {
      const level = this.latestAnalysis.alarmLevel || 'normal';
      return [
        { name: 'normal', value: level === 'normal' ? 1 : 0 },
        { name: 'warning', value: level === 'warning' ? 1 : 0 },
        { name: 'danger', value: level === 'danger' ? 1 : 0 }
      ];
    },
    formatNumber(value, decimals) {
      if (value === undefined || value === null) {
        return '-';
      }
      return Number(value).toFixed(decimals);
    },
    formatPercent(value) {
      if (value === undefined || value === null) {
        return '-';
      }
      return `${(Number(value) * 100).toFixed(1)}%`;
    },
    formatDateTime(value) {
      if (!value) {
        return '-';
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return '-';
      }
      return date.toLocaleString(this.language === 'ko' ? 'ko-KR' : 'en-US');
    },
    buildAlarmDisplayMessage(alarm) {
      const level = this.tLevel(alarm.alarmLevel);
      const score = this.formatNumber(alarm.anomalyScore, 4);
      const rms = this.formatNumber(alarm.rms, 5);
      const peakToPeak = this.formatNumber(alarm.peakToPeak, 5);
      const kurtosis = this.formatNumber(alarm.kurtosis, 3);
      if (this.language === 'ko') {
        return `${alarm.equipmentCode} ${level}: 이상점수 ${score}, RMS ${rms}, P2P ${peakToPeak}, Kurtosis ${kurtosis}`;
      }
      return `${alarm.equipmentCode} ${level}: score ${score}, RMS ${rms}, P2P ${peakToPeak}, kurtosis ${kurtosis}`;
    },
    toTime(value) {
      if (typeof value === 'number') {
        return value;
      }
      return new Date(value).getTime();
    }
  }
};
</script>
