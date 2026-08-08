# 12 Redis Strategy

## 목적

Redis는 MySQL을 대체하지 않는다.

사용처:

1. 관광공사 API cache
2. AI Job 상태
3. 임시 공유/세션 데이터
4. rate limit이 필요할 경우

## Cache

예:

```text
tour:places:gangneung:{queryHash}
tour:place:{contentId}
tour:related:{contentId}
```

TTL은 데이터 성격에 따라 설정한다.

정확한 TTL은 구현 시 configuration으로 분리한다.

## Job

```text
composition:job:{jobId}
```

value:

```json
{
  "status": "COMPOSITING",
  "progress": 60,
  "providerJobId": "..."
}
```

DB에도 필요한 이력을 남기되 polling 응답은 Redis에서 빠르게 읽을 수 있다.

## Cache miss

```text
Redis miss
 ↓
Tour API
 ↓
normalize
 ↓
Redis
 ↓
response
```

## Redis 장애

Redis가 없어도 핵심 영속 데이터가 깨지지 않도록 한다.

- 장소: MySQL/외부 API fallback
- Job: DB status fallback 가능 구조
- rate limit/cache: 기능 일부 저하 허용

## 직렬화

JSON 직렬화를 사용하고 Java serialization에 강하게 의존하지 않는다.

## TTL

모든 임시 key는 TTL을 설정한다.

영구 데이터를 Redis에만 두지 않는다.
