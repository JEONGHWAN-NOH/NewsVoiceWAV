package com.newsvoice.radio;

import java.util.*;
import java.util.regex.*;

final class Captions {
    record Cue(long start, long end, String text) {}
    private static final Pattern TIME = Pattern.compile("(\\d{2,}):(\\d{2}):(\\d{2})[,.](\\d{3})");
    static long time(String s) {
        Matcher m = TIME.matcher(s.trim());
        if (!m.matches()) throw new IllegalArgumentException("Invalid SRT timestamp");
        return ((Long.parseLong(m.group(1))*60+Long.parseLong(m.group(2)))*60+Long.parseLong(m.group(3)))*1000+Long.parseLong(m.group(4));
    }
    static List<Cue> parse(String input) {
        List<Cue> result = new ArrayList<>();
        for (String block : input.replace("\uFEFF", "").replace("\r", "").trim().split("\\n\\s*\\n")) {
            String[] lines = block.split("\\n");
            int i = lines.length > 0 && lines[0].trim().matches("\\d+") ? 1 : 0;
            if (lines.length <= i+1 || !lines[i].contains("-->")) throw new IllegalArgumentException("Invalid SRT cue");
            String[] t = lines[i].split("-->", 2);
            long start = time(t[0]), end = time(t[1]);
            if (end <= start || (!result.isEmpty() && start < result.get(result.size()-1).start())) throw new IllegalArgumentException("Invalid cue order");
            result.add(new Cue(start,end,String.join("\n", Arrays.copyOfRange(lines,i+1,lines.length)).trim()));
        }
        if(result.isEmpty()) throw new IllegalArgumentException("Empty SRT");
        return result;
    }
    static int index(List<Cue> cues, long position) {
        int lo=0, hi=cues.size()-1, found=-1;
        while(lo<=hi){int mid=(lo+hi)/2; if(cues.get(mid).start()<=position){found=mid;lo=mid+1;}else hi=mid-1;}
        return found;
    }
}
