package com.zyyqq.desk.service;

import com.zyyqq.desk.config.AppProperties;
import com.zyyqq.desk.repository.StatsRepository;
import com.zyyqq.desk.repository.TicketRepository;
import com.zyyqq.desk.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final StatsRepository statsRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final RedisSupport redisSupport;
    private final AppProperties props;

    public StatsService(StatsRepository statsRepository, TicketRepository ticketRepository,
                        UserRepository userRepository, RedisSupport redisSupport, AppProperties props) {
        this.statsRepository = statsRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.redisSupport = redisSupport;
        this.props = props;
    }

    public Map<String, Object> overview() {
        // 首页统计热点缓存：短 TTL，Redis 不可用时直接回源 MySQL
        return redisSupport.cacheJson("stats_overview", Map.class,
                Duration.ofSeconds(props.getRedis().getStatsOverviewTtl()),
                this::loadOverview);
    }

    private Map<String, Object> loadOverview() {
        Map<String, Object> data = new HashMap<>();
        long total = ticketRepository.countAll();
        long pending = ticketRepository.countByStatus("PENDING") + ticketRepository.countByStatus("ASSIGNED");
        long processing = ticketRepository.countByStatus("PROCESSING");
        long resolved = ticketRepository.countByStatus("RESOLVED") + ticketRepository.countByStatus("CLOSED");

        // 超时：当前时间超过解决目标时间且未到终态
        long overdue = countOverdue();

        data.put("totalTickets", total);
        data.put("pendingTickets", pending);
        data.put("processingTickets", processing);
        data.put("resolvedTickets", resolved);
        data.put("overdueTickets", overdue);
        data.put("todayNew", statsRepository.countNew(1));
        data.put("avgResponseMinutes", statsRepository.avgFirstResponseMinutes(7));
        data.put("avgResolveHours", statsRepository.avgResolveHours(7));
        data.put("satisfaction", statsRepository.avgSatisfaction(30));
        data.put("customerCount", userRepository.countByRole("CUSTOMER"));
        data.put("agentCount", userRepository.countByRole("AGENT"));

        data.put("trend", statsRepository.trend(7));
        data.put("categoryDistribution", statsRepository.categoryDistribution());
        data.put("statusDistribution", statsRepository.statusDistribution());
        data.put("priorityDistribution", statsRepository.priorityDistribution());
        data.put("agentWorkload", statsRepository.agentWorkload());
        data.put("satisfactionDistribution", statsRepository.satisfaction());
        return data;
    }

    public List<Map<String, Object>> trend(int days) {
        return statsRepository.trend(Math.min(Math.max(days, 1), 90));
    }

    private long countOverdue() {
        // 终态：RESOLVED/CLOSED；非终态且 sla_resolve_at 已过为超时
        return statsRepository.countOverdueInternal();
    }
}
