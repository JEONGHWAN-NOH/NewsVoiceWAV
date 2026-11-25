package com.newsvoice.tts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline, conservative preflight for Korean news narration.
 * The app is not a Korean grammar LLM: it never rewrites news facts, names or numbers.
 * Unresolved colloquial endings must be reviewed instead of silently narrated.
 */
public final class BroadcastScriptReview {
    private BroadcastScriptReview() {}
    public static final class Result {
        public final String reviewed;
        public final List<String> warnings;
        public final int corrections;
        private Result(String text, List<String> warnings, int corrections) {
            reviewed=text;
            this.warnings=Collections.unmodifiableList(new ArrayList<>(warnings));
            this.corrections=corrections;
        }
        public boolean approved(){ return warnings.isEmpty(); }
        public String summary(){
            if(approved())return "정중한 방송체 사전 검수 통과 · 자동 보정 "+corrections+"건. 맥락상 맞춤법과 뉴스 사실은 별도 확인이 필요합니다.";
            return "방송체 검토 필요 ("+warnings.size()+"건): "+String.join(" / ",warnings);
        }
    }

    private static final String[][] ENDINGS={
        {"발표했어요","발표했습니다"},{"밝혔어요","밝혔습니다"},
        {"설명했어요","설명했습니다"},{"보도했어요","보도했습니다"},
        {"확인됐어요","확인됐습니다"},{"전했어요","전했습니다"},
        {"발표했다","발표했습니다"},{"설명했다","설명했습니다"},
        {"보도했다","보도했습니다"},{"밝혔다","밝혔습니다"},
        {"전했다","전했습니다"},{"나타났다","나타났습니다"},
        {"확인됐다","확인됐습니다"},{"진행됐다","진행됐습니다"},
        {"예정이다","예정입니다"},{"계획이다","계획입니다"},
        {"였어요","였습니다"},{"였다","였습니다"},
        {"했어요","했습니다"},{"했어","했습니다"},{"했다","했습니다"},
        {"됐어요","됐습니다"},{"됐다","됐습니다"},
        {"이에요","입니다"},{"예요","입니다"},
        {"아니다","아닙니다"},{"이다","입니다"},
        {"있다","있습니다"},{"없다","없습니다"},
        {"된다","됩니다"},{"한다","합니다"}
    };
    private static final Pattern ENDING_WORD=Pattern.compile("([가-힣]+)([.!?])(?=\\s|$)");
    private static final Pattern HANGUL_AT_END=Pattern.compile("([가-힣]+)$");

    public static Result review(String raw) {
        if(raw==null || raw.trim().isEmpty())
            return new Result("",Collections.singletonList("원고가 비어 있습니다."),0);
        String source=raw.replace("\r\n","\n").replace('\r','\n').replace('\u00a0',' ');
        String[] lines=source.split("\n",-1);
        List<String> warnings=new ArrayList<>();
        StringBuilder out=new StringBuilder();
        int corrections=0;
        for(int i=0;i<lines.length;i++){
            String before=lines[i];
            String line=before.replace('\t',' ').replaceAll(" {2,}"," ").trim();
            if(line.equals("===")||line.equals("---")){
                addLine(out,line);
                if(!before.equals(line))corrections++;
                continue;
            }
            if(line.isEmpty()){
                addLine(out,"");
                continue;
            }
            {
                line=line.replaceAll(" +([,，。!?])","$1")
                    .replace('，',',').replace('。','.')
                    .replaceAll("(?<=[가-힣A-Za-z]),(?=[가-힣A-Za-z])",", ")
                    .replaceAll("([.!?])(?=[가-힣])","$1 ")
                    .replaceAll(" {2,}"," ");
                // Preserve verbatim quotes and URLs; potentially informal quotes require manual review.
                boolean preserveQuotes=line.indexOf('"')>=0||line.contains("“")||line.contains("”")||
                    line.contains("‘")||line.contains("’")||line.contains("https://")||line.contains("http://");
                if(!preserveQuotes){
                    for(String[] pair:ENDINGS){
                        Matcher m=Pattern.compile(Pattern.quote(pair[0])+"(?=\\.(?:\\s|$)|$)").matcher(line);
                        if(m.find())line=m.replaceAll(Matcher.quoteReplacement(pair[1]));
                    }
                }
                if(line.matches(".*(?:습니다|입니다|겠습니다|됩니다|됐습니다|세요)$"))line+=".";
                checkSentenceEndings(line,i+1,warnings);
            }
            if(!before.equals(line))corrections++;
            addLine(out,line);
        }
        String result=out.toString().trim();
        if(result.isEmpty())warnings.add("원고에 문장이 없습니다.");
        if(result.length()>60000)warnings.add("원고가 60,000자를 초과합니다.");
        return new Result(result,warnings,corrections);
    }

    private static void addLine(StringBuilder out,String line){
        if(out.length()>0)out.append('\n');
        out.append(line);
    }
    private static void checkSentenceEndings(String line,int number,List<String> warnings){
        Matcher m=ENDING_WORD.matcher(line);
        while(m.find()){
            if(!formal(m.group(1))){
                String token=m.group(1);
                if(token.length()>24)token=token.substring(token.length()-24);
                warnings.add("줄 "+number+" 문장 끝맺음: "+token+m.group(2));
            }
        }
        if(!line.matches(".*[.!?]$")){
            Matcher tail=HANGUL_AT_END.matcher(line);
            if(tail.find()&&!formal(tail.group(1)))
                warnings.add("줄 "+number+" 문장 끝맺음 또는 마침표를 확인해 주세요.");
        }
    }
    private static boolean formal(String w){
        return (w.endsWith("니다")&&!w.endsWith("아니다"))||w.endsWith("겠습니다")||
            w.endsWith("습니까")||w.endsWith("십니까")||w.endsWith("합니까")||w.endsWith("입니까")||w.endsWith("세요")||
            w.endsWith("까요")||w.endsWith("됩니다")||w.endsWith("됐습니다");
    }
}
