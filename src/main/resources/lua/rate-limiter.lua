local key = KEYS[1]
local limit = tonumber(ARGV[1])
local expire_seconds = tonumber(ARGV[2])

local current = redis.call("INCR", key)

if tonumber(current) == 1 then
  redis.call("EXPIRE", key, expire_seconds)
end

return current
