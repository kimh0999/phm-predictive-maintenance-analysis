CREATE DATABASE IF NOT EXISTS smart_factory
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'smart_factory'@'localhost'
  IDENTIFIED BY 'smart_factory';

GRANT ALL PRIVILEGES ON smart_factory.* TO 'smart_factory'@'localhost';

USE smart_factory;

CREATE TABLE IF NOT EXISTS equipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_code VARCHAR(50) NOT NULL UNIQUE,
  equipment_name VARCHAR(100) NOT NULL,
  location VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vibration_window (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_code VARCHAR(50) NOT NULL,
  measured_at DATETIME(3) NOT NULL,
  sampling_rate INT NOT NULL,
  rpm INT,
  window_size INT NOT NULL,
  window_index BIGINT NOT NULL,
  raw_file_path VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_vibration_window_equipment_time (equipment_code, measured_at),
  CONSTRAINT fk_vibration_window_equipment
    FOREIGN KEY (equipment_code) REFERENCES equipment (equipment_code)
);

CREATE TABLE IF NOT EXISTS analysis_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  vibration_window_id BIGINT NOT NULL,
  equipment_code VARCHAR(50) NOT NULL,
  rms DOUBLE,
  peak_frequency DOUBLE,
  peak_to_peak DOUBLE,
  crest_factor DOUBLE,
  kurtosis DOUBLE,
  prediction VARCHAR(50),
  confidence DOUBLE,
  model_version VARCHAR(100),
  model_input_type VARCHAR(50),
  model_input_size INT,
  model_expected_input_size INT,
  model_input_strategy VARCHAR(150),
  model_status VARCHAR(50),
  anomaly_score DOUBLE,
  alarm_level VARCHAR(20),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_analysis_result_equipment_created (equipment_code, created_at),
  CONSTRAINT fk_analysis_result_window
    FOREIGN KEY (vibration_window_id) REFERENCES vibration_window (id),
  CONSTRAINT fk_analysis_result_equipment
    FOREIGN KEY (equipment_code) REFERENCES equipment (equipment_code)
);

CREATE TABLE IF NOT EXISTS alarm_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_code VARCHAR(50) NOT NULL,
  analysis_result_id BIGINT NOT NULL,
  alarm_level VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'open',
  message VARCHAR(255) NOT NULL,
  occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at DATETIME,
  duration_seconds BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_alarm_history_equipment_time (equipment_code, occurred_at),
  INDEX idx_alarm_history_equipment_status (equipment_code, status, occurred_at),
  CONSTRAINT fk_alarm_history_equipment
    FOREIGN KEY (equipment_code) REFERENCES equipment (equipment_code),
  CONSTRAINT fk_alarm_history_analysis
    FOREIGN KEY (analysis_result_id) REFERENCES analysis_result (id)
);
