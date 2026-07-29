package ec.edu.espe.backend.reactive.model;

import java.time.LocalDateTime;

public class ReactiveClaimEvent {

    private String eventId;
    private ClaimEventType type;
    private Long entityId;
    private String itemName;
    private String userName;
    private String status;
    private String description;
    private LocalDateTime timestamp;

    public ReactiveClaimEvent() {}

    public ReactiveClaimEvent(String eventId, ClaimEventType type, Long entityId,
                               String itemName, String userName, String status, String description) {
        this.eventId = eventId;
        this.type = type;
        this.entityId = entityId;
        this.itemName = itemName != null ? itemName : "N/A";
        this.userName = userName != null ? userName : "N/A";
        this.status = status;
        this.description = description;
        this.timestamp = LocalDateTime.now();
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public ClaimEventType getType() { return type; }
    public void setType(ClaimEventType type) { this.type = type; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
