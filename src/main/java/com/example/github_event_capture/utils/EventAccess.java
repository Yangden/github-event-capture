package com.example.github_event_capture.utils;

import java.util.HashMap;
import com.example.github_event_capture.entity.Event;
import com.example.github_event_capture.entity.dto.IssueEventDTO;
import com.example.github_event_capture.entity.dto.PushEventDTO;

/* obtain different types of event objects based on key value */
public class EventAccess {
    private static final HashMap<String, Class<? extends Event>> EventMap;
    static {
        EventMap = new HashMap<>();
        EventMap.put("issues", IssueEventDTO.class);
        EventMap.put("push", PushEventDTO.class);
    }

    public static Class<? extends Event> getEventObj (String Key) {
        return EventMap.get(Key);
    }


}
