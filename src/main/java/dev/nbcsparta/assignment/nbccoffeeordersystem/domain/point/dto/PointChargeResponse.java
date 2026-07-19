package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto;

/**
 * 포인트 충전 결과를 표현한다.
 *
 * @param userId 포인트를 충전한 사용자 식별자
 * @param chargedAmount 충전된 포인트 수
 * @param balance 충전 후 포인트 잔액
 */
public record PointChargeResponse(
        Long userId,
        Long chargedAmount,
        Long balance
) {

    /**
     * 포인트 충전 결과로 응답을 생성한다.
     *
     * @param userId 포인트를 충전한 사용자 식별자
     * @param chargedAmount 충전된 포인트 수
     * @param balance 충전 후 포인트 잔액
     * @return 생성된 포인트 충전 응답
     */
    public static PointChargeResponse of(long userId, long chargedAmount, long balance) {
        return new PointChargeResponse(userId, chargedAmount, balance);
    }
}
