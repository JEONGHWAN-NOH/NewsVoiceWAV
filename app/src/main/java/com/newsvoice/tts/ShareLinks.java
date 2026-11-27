package com.newsvoice.tts;

import java.net.URI;

/** Validate actual video links, not channel or search pages. */
final class ShareLinks {
    static String youtube(String input) {
        String s=input.trim();
        if (!s.contains("://")) s="https://"+s;
        try {
            URI u=URI.create(s); String host=u.getHost();
            if (!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1) return "";
            String path=u.getPath(), id="";
            if(host.equalsIgnoreCase("youtu.be")) id=path.substring(1);
            else if(host.matches("(?i)(www\\.|m\\.)?youtube\\.com")) {
                if(path.equals("/watch")&&u.getRawQuery()!=null) {
                    for(String q:u.getRawQuery().split("&")) if(q.startsWith("v=")) {id=q.substring(2);break;}
                } else if(path.matches("/(shorts|live|embed)/[^/]+/?")) id=path.split("/")[2];
            }
            return id.matches("[A-Za-z0-9_-]{11}")?"https://www.youtube.com/watch?v="+id:"";
        } catch(RuntimeException e){return "";}
    }
    static String mp4(String input) {
        try {
            URI u=URI.create(input.trim());
            return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".mp4")?u.toASCIIString():"";
        } catch(RuntimeException e){return "";}
    }
    static String drive(String input) {
        try {
            URI u=URI.create(input.trim());
            if(!"https".equalsIgnoreCase(u.getScheme())||!"drive.google.com".equalsIgnoreCase(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1)return "";
            String id="", resourceKey="";
            String path=u.getPath();
            if(path.matches("/file/d/[A-Za-z0-9_-]+(?:/view|/preview)?/?"))id=path.split("/")[3];
            if(u.getRawQuery()!=null)for(String q:u.getRawQuery().split("&")){
                if((path.equals("/open")||path.equals("/uc"))&&q.startsWith("id="))id=q.substring(3);
                if(q.startsWith("resourcekey="))resourceKey=q.substring(12);
            }
            if(!id.matches("[A-Za-z0-9_-]{10,200}")||(!resourceKey.isEmpty()&&!resourceKey.matches("[A-Za-z0-9_-]+")))return "";
            return "https://drive.google.com/file/d/"+id+"/view"+(resourceKey.isEmpty()?"":"?resourcekey="+resourceKey);
        }catch(RuntimeException e){return "";}
    }
    static String message(String kakao,String youtube) {
        return "오늘의 종합뉴스\n\n▶ 카카오톡에서 보기 (경량 MP4)\n"+(kakao.isEmpty()?"함께 전송된 동영상을 눌러 시청하세요.":kakao)+"\n\n▶ YouTube에서 보기 (스트리밍)\n"+youtube;
    }
}
