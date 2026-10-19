package com.example.blog.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Bounded LCS: O(m*n) time/space, at most 600 lines per side. Never emits HTML. */
@Service
public class RevisionDiffService {
    private static final int MAX_LINES = 600;

    public Comparison compare(String before, String after) {
        var oldLines = lines(before);
        var newLines = lines(after);
        if (oldLines.size() > MAX_LINES || newLines.size() > MAX_LINES) {
            return new Comparison(List.of(), 0, 0, true);
        }
        int[][] lcs = new int[oldLines.size() + 1][newLines.size() + 1];
        for (int i = oldLines.size() - 1; i >= 0; i--) {
            for (int j = newLines.size() - 1; j >= 0; j--) {
                lcs[i][j] = oldLines.get(i).equals(newLines.get(j)) ? 1 + lcs[i + 1][j + 1]
                    : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<DiffLine> output = new ArrayList<>();
        int i = 0, j = 0, added = 0, removed = 0;
        while (i < oldLines.size() || j < newLines.size()) {
            if (i < oldLines.size() && j < newLines.size() && oldLines.get(i).equals(newLines.get(j))) {
                output.add(new DiffLine("same", i + 1, j + 1, " ", oldLines.get(i)));
                i++; j++;
            } else if (i < oldLines.size() && (j == newLines.size() || lcs[i + 1][j] >= lcs[i][j + 1])) {
                output.add(new DiffLine("removed", i + 1, null, "−", oldLines.get(i++)));
                removed++;
            } else {
                output.add(new DiffLine("added", null, j + 1, "+", newLines.get(j++)));
                added++;
            }
        }
        return new Comparison(List.copyOf(output), added, removed, false);
    }

    private List<String> lines(String source) {
        return source == null || source.isEmpty() ? List.of()
            : List.of(source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }

    public record DiffLine(String kind, Integer oldNumber, Integer newNumber, String marker, String text) { }
    public record Comparison(List<DiffLine> lines, int added, int removed, boolean tooLarge) { }
}
