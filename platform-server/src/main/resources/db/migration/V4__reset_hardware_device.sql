-- 旧版本把推理硬件默认写死为 cpu，用户从未真正选择过；清空后由硬件探测按独显、核显、CPU 回退
UPDATE settings
SET value = replace(value, '"hardwareDeviceId":"cpu"', '"hardwareDeviceId":""')
WHERE key = 'app.settings';
