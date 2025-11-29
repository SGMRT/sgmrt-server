local key = KEYS[1]
local limit = tonumber(ARGV[1])
local expire_seconds = tonumber(ARGV[2])

local current = redis.call("GET", key)
if current ~= false and tonumber(current) >= limit then
  return -1
end

local new_count = redis.call("INCR", key)

if tonumber(new_count) == 1 then
  redis.call("EXPIRE", key, expire_seconds)
end

return new_count
