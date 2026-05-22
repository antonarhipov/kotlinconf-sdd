CREATE TABLE temperature_readings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    datetime DATETIME NOT NULL,
    temp DECIMAL(5,2) NOT NULL,
    CONSTRAINT uk_name_datetime UNIQUE (name, datetime)
);
