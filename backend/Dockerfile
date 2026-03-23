FROM golang:1.25.5 AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -o yousightseeing ./cmd/app/main.go


FROM alpine:3.20

WORKDIR /app

RUN apk --no-cache add ca-certificates

COPY --from=builder /app/yousightseeing .

COPY migrations ./migrations

RUN mkdir -p ./docs
COPY docs/ ./docs/

ENV HTTP_ADDRESS=:8080

CMD ["./yousightseeing"]
