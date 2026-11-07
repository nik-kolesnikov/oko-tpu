package ru.tpu.oko.catalog.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "widget_settings")
public class WidgetSettings {

    public static final String DEFAULT_QUESTION = "Как вам сервис?";
    public static final short DEFAULT_ASK_INTERVAL_DAYS = 14;
    public static final short DEFAULT_COMMENT_THRESHOLD = 3;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private String question;

    @Column(name = "ask_interval_days", nullable = false)
    private short askIntervalDays;

    @Column(name = "comment_threshold", nullable = false)
    private short commentThreshold;

    @Convert(converter = WidgetPosition.JpaConverter.class)
    @Column(nullable = false)
    private WidgetPosition position;

    @Column(name = "widget_enabled", nullable = false)
    private boolean widgetEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WidgetSettings() {
    }

    public static WidgetSettings defaults(long productId, Instant now) {
        WidgetSettings s = new WidgetSettings();
        s.productId = productId;
        s.question = DEFAULT_QUESTION;
        s.askIntervalDays = DEFAULT_ASK_INTERVAL_DAYS;
        s.commentThreshold = DEFAULT_COMMENT_THRESHOLD;
        s.position = WidgetPosition.BOTTOM_RIGHT;
        s.widgetEnabled = true;
        s.updatedAt = now;
        return s;
    }

    public void update(String question, int askIntervalDays, int commentThreshold, WidgetPosition position,
                       boolean widgetEnabled, Instant now) {
        this.question = question;
        this.askIntervalDays = (short) askIntervalDays;
        this.commentThreshold = (short) commentThreshold;
        this.position = position;
        this.widgetEnabled = widgetEnabled;
        this.updatedAt = now;
    }

    public void disable(Instant now) {
        this.widgetEnabled = false;
        this.updatedAt = now;
    }

    public Long getProductId() {
        return productId;
    }

    public String getQuestion() {
        return question;
    }

    public int getAskIntervalDays() {
        return askIntervalDays;
    }

    public int getCommentThreshold() {
        return commentThreshold;
    }

    public WidgetPosition getPosition() {
        return position;
    }

    public boolean isWidgetEnabled() {
        return widgetEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
