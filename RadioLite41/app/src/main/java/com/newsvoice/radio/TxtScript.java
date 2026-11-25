package com.newsvoice.radio;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

final class TxtScript {
    static String decode(byte[] raw) throws CharacterCodingException {
        if(raw.length>=2 && (raw[0]&255)==255 && (raw[1]&255)==254)return new String(raw,2,raw.length-2,StandardCharsets.UTF_16LE);
        if(raw.length>=2 && (raw[0]&255)==254 && (raw[1]&255)==255)return new String(raw,2,raw.length-2,StandardCharsets.UTF_16BE);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString().replaceFirst("^\\uFEFF","");}
        catch(CharacterCodingException e){return Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
    }
    static List<String> split(String content){
        if(content.indexOf('\u0000')>=0)throw new IllegalArgumentException("Invalid text encoding");
        List<String> rows=new ArrayList<>();
        for(String line:content.replace("\r","").split("\\n+")){
            for(String p:line.trim().split("(?<=[.!?。！？])\\s+")){
                String t=p.trim();while(t.length()>250){int n=t.lastIndexOf(' ',250);if(n<30)n=250;rows.add(t.substring(0,n).trim());t=t.substring(n).trim();}if(!t.isEmpty())rows.add(t);
            }
        }
        if(rows.isEmpty())throw new IllegalArgumentException("Empty script");return rows;
    }
    static List<Captions.Cue> estimate(List<String> rows,long duration){
        long total=0;for(String row:rows)total+=Math.max(1,row.length());
        List<Captions.Cue> cues=new ArrayList<>();long weight=0;for(String row:rows){long start=duration*weight/total;weight+=Math.max(1,row.length());long end=duration*weight/total;cues.add(new Captions.Cue(start,Math.max(start+1,end),row));}return cues;
    }
    static String toSrt(List<Captions.Cue> cues){StringBuilder out=new StringBuilder();int n=0;for(Captions.Cue c:cues)out.append(++n).append('\n').append(stamp(c.start())).append(" --> ").append(stamp(c.end())).append('\n').append(c.text()).append("\n\n");return out.toString();}
    private static String stamp(long ms){return String.format(Locale.ROOT,"%02d:%02d:%02d,%03d",ms/3600000,ms/60000%60,ms/1000%60,ms%1000);}
}
