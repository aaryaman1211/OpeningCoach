package com.openingcoach.core.analysis;

import java.util.List;

/** Formats a move path as readable text: ["e4","c5","Nf3"] -> "1.e4 c5 2.Nf3". */
public final class MoveText {

    private MoveText() {
    }

    public static String format(List<String> path) {
        if (path.isEmpty()) {
            return "(start position)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i % 2 == 0) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(i / 2 + 1).append('.');
            } else {
                sb.append(' ');
            }
            sb.append(path.get(i));
        }
        return sb.toString();
    }
}
