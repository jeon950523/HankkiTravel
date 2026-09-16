package kr.hankkitravel.recommendation.application;
/** Official regional-demand boundary. Missing data remains NOT_EVALUATED. */
public interface AreaDemandSignalProvider { Signal signal(String region,java.time.LocalDate date); record Signal(boolean evaluated,int score,String reason){public static Signal notEvaluated(){return new Signal(false,0,"연결된 공식 지역 수요 제공자가 없어 평가하지 않았어요.");}} }
