---
name: server-vladelca-d-lmpc-server
description: "Сервер «Чумы» владельца — D:\LMPC-server, запуск start.bat, играем через Radmin 26.80.6.64:25565"
metadata:
  node_type: memory
  type: project
---

У владельца свой сервер «Чумы» в `D:\LMPC-server`, поднят 2026-09-08.
Запуск — двойной щелчок по `start.bat`, окно cmd; остановка — команда
`stop`, не крестиком. Играем через Radmin VPN, адрес `26.80.6.64:25565`
(домашняя сеть — `192.168.0.10:25565`).

**Why:** сервер из заметки `2026-09-06-server-podnyat.md` стоит
у напарника на `E:\CLAUDE\server` — на машине владельца диска `E:`
вообще нет. Это второй, независимый сервер, а не тот же самый.

**How to apply:** Java 21.0.9 лежит в PATH
(`C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`),
`D:\JDK21` из старой заметки на этой машине не существует. Серверу
отдано 6 ГБ из 16 (владелец играет на той же машине). Место тесное:
на C: и D: по ~24 ГБ свободно.

Подробности, дифф модов и список несделанного руками —
в `docs/superpowers/notes/2026-09-08-vtoroj-server-na-mashine-vladelca.md`.

Связано: [[parallelnaya-sessiya-v-plaguecore]]
