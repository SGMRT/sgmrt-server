local key = KEYS[1]

-- 카운터 감소
local current = redis.call("DECR", key)

-- 값이 0 이하라면 키 삭제
if tonumber(current) <= 0 then
  redis.call("DEL", key)
  return 0
end

return current
