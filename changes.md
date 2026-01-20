# Guara

## Release v1.1.12
on: 20/Jan/2026

 - Moving src/main/resources/logback.xml to src/test/resources/logback-test.xml

## Release v1.1.11
on: 17/Dec/2025

 - Including '&' and ',' for json regex validation

## Release v1.1.10
on: 19/Nov/2025

 - Do not log errors at `ensureResponse`
 - Upgrading `commons-lang3` (3.17.0 to 3.20.0) because of CVE-2025-48924

## Release v1.1.9
on: 01/Sep/2025

 - Fixed serialization for `UnifiedErrorFormat`

## Release v1.1.8
on: 12/Jun/2025

 - Updating zio dependencies
 - Added `HttpConfig.maxHeaderSize` and `HttpConfig.maxLineSize`

## Release v1.1.7
on: 28/May/2025

 - Added `CorsConfig.from(String)`
 - `GuaraApp.services` is now public 

## Release v1.1.6
on: 27/May/2025

 - Added `startGuaraNoConfig`

## Release v1.1.5
on: 24/Mar/2025

 - Propagating the root exception at ReturnResponseWithExceptionError

## Release v1.1.4
on: 24/Mar/2025

 - Sandboxing erros at `ensureResponse`
 - Returning structure error information as json

## Release v1.1.3
on: 21/Mar/2025

 - Fixed previous release

## Release v1.1.2
on: 21/Mar/2025

 - Added option to log the body when `parse` fails

## Release v1.1.1
on: 13/Mar/2025

 - Updating dependencies

## Release v1.1.0
on: 13/Mar/2025

 - Reworking how background services start

## Release v1.0.1
on: 21/Feb/2025

 - Added http.client code
 - Added http.urlFrom

## Release v1.0.0
on: 21/Feb/2025

 - Our first stable release. Yay!
