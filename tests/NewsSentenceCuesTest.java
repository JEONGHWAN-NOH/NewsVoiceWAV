import com.newsvoice.tts.NewsSentenceCues;
import java.util.List;
public final class NewsSentenceCuesTest {
    public static void main(String[] args) {
        String sample="광명시가 발표했습니다. 두 번째 문장입니다!\n세 번째 문장입니다. 환율 1.25가 아닌 예시입니다.";
        List<String> pieces=NewsSentenceCues.split(sample);
        if(pieces.size()!=4)throw new AssertionError("Sentence split count: "+pieces);
        int[] ends=NewsSentenceCues.endCharacterOffsets(sample);
        if(ends.length!=4 || ends[3]!=NewsSentenceCues.countVisible(sample))
            throw new AssertionError("Highlight offsets lost text");
        StringBuilder s=new StringBuilder();
        for(String piece:pieces)s.append(piece.replaceAll("\\s+",""));
        if(!s.toString().equals(sample.replaceAll("\\s+","")))
            throw new AssertionError("Text lost");
        if(NewsSentenceCues.split("문장 하나만 있습니다").size()!=1)
            throw new AssertionError("No punctuation paragraph");
        System.out.println("PASS: sentence boundaries and highlight offsets");
    }
}
