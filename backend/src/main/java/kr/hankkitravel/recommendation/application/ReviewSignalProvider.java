package kr.hankkitravel.recommendation.application;
/** Official review boundary. Implementations must not scrape HTML. */
public interface ReviewSignalProvider { Signal signal(String contentId); record Signal(boolean evaluated,int score,String reason){public static Signal notEvaluated(){return new Signal(false,0,"연결된 공식 후기 제공자가 없어 평가하지 않았어요.");}} }
