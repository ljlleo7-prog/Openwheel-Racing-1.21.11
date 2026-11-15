package com.openwheelracing.content.entity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Opt-in ring buffer. Driving never performs file I/O or writes the normal server log. */
final class HandlingTrace {
    static final int CAPACITY = 20000;
    static final String HEADER = "event,tick,epoch,frame,correction,x,y,z,vx,vy,vz,yaw,yaw_rate,steering,"
        + "front_surface_c,rear_surface_c,front_carcass_c,rear_carcass_c,damage,front_carcass_synced_c,rear_carcass_synced_c,graining,patching,lateral_force_fl,lateral_force_fr,lateral_force_rl,lateral_force_rr";
    private final ArrayDeque<String> rows = new ArrayDeque<>();
    private long dropped;

    void add(String row) {
        if (rows.size() == CAPACITY) { rows.removeFirst(); dropped++; }
        rows.addLast(row);
    }
    int size() { return rows.size(); }
    long dropped() { return dropped; }
    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> output = new ArrayList<>(rows.size() + 2);
        output.add("# dropped_oldest_rows=" + dropped);
        output.add(HEADER);
        output.addAll(rows);
        Files.write(path, output, java.nio.charset.StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
