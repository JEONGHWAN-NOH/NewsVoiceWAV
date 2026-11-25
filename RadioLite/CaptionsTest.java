package com.newsvoice.radio;
import java.util.*;
public class CaptionsTest {
    public static void main(String[] args) {
        StringBuilder s=new StringBuilder();
        for(int i=0;i<12;i++){int start=6+2*i; s.append(i+1).append("\n00:00:").append(String.format(Locale.ROOT,"%02d",start)).append(",000 --> 00:00:").append(String.format(Locale.ROOT,"%02d",start+2)).append("뉴스 ").append(i+1).append("\n\n");}
        var cues=Captions.parse(s.toString());
        if(cues.size()!=12||Captions.index(cues,5999)!=-1||Captions.index(cues,6000)!=0||Captions.index(cues,28000)!=11)throw new AssertionError("caption timing");
        if(!cues.get(11).text().equals("뉴스 12"))throw new AssertionError("Korean text");
        try {Captions.parse("1\n00:00:03,000 --> 00:00:02,000\nBad");throw new AssertionError("bad accepted");} catch(IllegalArgumentException expected){}
        System.out.println("Caption tests passed");
    }
}
