package com.ctrip.framework.apollo.core.schedule;

/**
 * 基于指数级计算的定时策略实现类。<br>
 * 
 * @author Jason Song(song_s@ctrip.com)
 */
public class ExponentialSchedulePolicy implements SchedulePolicy {
	
	/**
     * 延迟时间下限
     */
	private final long delayTimeLowerBound;
	
	/**
     * 延迟时间上限
     */
	private final long delayTimeUpperBound;
	
	/**
     * 最后延迟执行时间
     */
	private long lastDelayTime;

	public ExponentialSchedulePolicy(long delayTimeLowerBound, long delayTimeUpperBound) {
		this.delayTimeLowerBound = delayTimeLowerBound;
		this.delayTimeUpperBound = delayTimeUpperBound;
	}

	/**************************
	例子===========
	 每次执行失败，调用 #fail() 方法，指数级计算新的延迟执行时间。
	 delayTimeLowerBound, delayTimeUpperBound= [1, 120] 执行 10 轮
		1 2 4 8 16 32 64 120 120 120
	 delayTimeLowerBound, delayTimeUpperBound= [30, 120] 执行 10 轮
		30 60 120 120 120 120 120 120 120 120 120 120

	 ***************************/
	
	@Override
	public long fail() {
		long delayTime = lastDelayTime;

		// 设置初始延迟时间
		if (delayTime == 0) {
			delayTime = delayTimeLowerBound;
		} else {
			// 延迟时间指数增长
			delayTime = Math.min(lastDelayTime << 1, delayTimeUpperBound);
		}

		// 更新最后的延迟时间
		lastDelayTime = delayTime;

		return delayTime;
	}

	@Override
	public void success() {
		lastDelayTime = 0;
	}
}
