package soma.ghostrunner.domain.course.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.query.CourseQueryModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CourseCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String KEY_PREFIX = "course:";
    private static final long TTL_IN_MINUTES = 60;

    public void save(CourseQueryModel model) {
        String key = buildKey(model.id());
        redisTemplate.opsForValue().set(key, model, TTL_IN_MINUTES, TimeUnit.MINUTES);
    }

    public void saveAll(List<CourseQueryModel> models) {
        // MSET으로 데이터 저장
        Map<String, CourseQueryModel> courseMap = models.stream()
                .collect(Collectors.toMap(
                        model -> buildKey(model.id()),
                        model -> model
                ));
        redisTemplate.opsForValue().multiSet(courseMap);

        // 각 키에 TTL 설정 (MSET만으로는 TTL 설정 불가)
        courseMap.keySet().forEach(key ->
            redisTemplate.expire(key, TTL_IN_MINUTES, TimeUnit.MINUTES)
        );
    }

    public CourseQueryModel findById(Long id) {
        String key = buildKey(id);
        return (CourseQueryModel) redisTemplate.opsForValue().get(key);
    }

    public Map<Long, CourseQueryModel> findAllById(List<Long> ids) {
        List<String> keys = ids.stream().map(this::buildKey).toList();
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        validateLengthEquality(keys, values);

        Map<Long, CourseQueryModel> results = new HashMap<>(ids.size());
        for(int i = 0; i < ids.size(); i++) {
            results.put(ids.get(i), (CourseQueryModel) values.get(i));
        }
        return results;
    }

    public void deleteById(Long id) {
        String key = buildKey(id);
        redisTemplate.delete(key);
    }

    public void update(Long id, CourseQueryModel model) {
        String key = buildKey(id);
        redisTemplate.opsForValue().set(key, model, TTL_IN_MINUTES, TimeUnit.MINUTES);
    }

    private void validateLengthEquality(List<?> ids, List<?> values) {
        if (ids == null || values == null) {
            throw new IllegalStateException("Redis multiGet returned null for ids or values");
        }
        if (ids.size() != values.size()) {
            throw new IllegalStateException("Redis multiGet returned unexpected number of results (id size = " + ids.size() + ", values size = " + values.size() + ")");
        }
    }

    private String buildKey(Long id) {
        return KEY_PREFIX + buildHashTag(id) + ":" + id;
    }

    private String buildHashTag(Long id) {
//        return "{" + id % HASH_TAG_MOD + "}";
        return "{0}";
    }

}
