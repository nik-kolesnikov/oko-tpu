package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Схема {@code ServiceUpdate} (PATCH). url и monitoringRef можно явно очистить null-ом. */
public class ServiceUpdate {

    @Size(min = 1, max = 200, message = "от 1 до 200 символов")
    @Pattern(regexp = "(?s).*\\S.*", message = "не должно состоять из пробелов")
    private String name;

    private String url;
    private boolean urlSet;

    @Size(max = 200, message = "не длиннее 200 символов")
    private String monitoringRef;
    private boolean monitoringRefSet;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
        this.urlSet = true;
    }

    public boolean isUrlSet() {
        return urlSet;
    }

    public String getMonitoringRef() {
        return monitoringRef;
    }

    public void setMonitoringRef(String monitoringRef) {
        this.monitoringRef = monitoringRef;
        this.monitoringRefSet = true;
    }

    public boolean isMonitoringRefSet() {
        return monitoringRefSet;
    }
}
