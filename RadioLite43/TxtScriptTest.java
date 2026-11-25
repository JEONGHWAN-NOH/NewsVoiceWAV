package com.newsvoice.radio;
import java.nio.charset.*;
import java.util.*;
public class TxtScriptTest {
    public static void main(String[] args)throws Exception {
        String s="첫 번째 뉴스입니다. 오늘의 주요 소식입니다.\n두 번째 뉴스입니다.";
        for(String charset:new String[]{"UTF-8","MS949","UTF-16"})if(!s.equals(TxtScript.decode(s.getBytes(Charset.forName(charset)))))throw new AssertionError(charset);
        if(!s.equals(TxtScript.decode(("\uFEFF"+s).getBytes(StandardCharsets.UTF_8))))throw new AssertionError("UTF8 BOM");
        var rows=TxtScript.split(s);if(rows.size()!=3)throw new AssertionError("split");
        var cues=TxtScript.estimate(rows,300000);if(cues.get(cues.size()-1).end()!=300000||Captions.parse(TxtScript.toSrt(cues)).size()!=3)throw new AssertionError("SRT");
        System.out.println("TXT decoding, splitting and SRT tests passed");
    }
}
