import { AppShell, Badge, Button, Card, Group, Stack, Text, Title } from '@mantine/core';

export default function App() {
  return (
    <AppShell header={{ height: 64 }} padding="lg">
      <AppShell.Header>
        <Group h="100%" px="lg" justify="space-between">
          <Title order={3}>Sample</Title>
          <Badge variant="light">React + Mantine</Badge>
        </Group>
      </AppShell.Header>

      <AppShell.Main>
        <Stack maw={720} mx="auto" gap="lg">
          <div>
            <Text c="dimmed" size="sm">
              Hexagonal multi-project
            </Text>
            <Title order={1}>Frontend is ready</Title>
          </div>
          <Card withBorder radius="md" padding="xl">
            <Stack gap="md">
              <Text>React와 TypeScript, Mantine으로 구성된 프론트엔드입니다.</Text>
              <Button w="fit-content">시작하기</Button>
            </Stack>
          </Card>
        </Stack>
      </AppShell.Main>
    </AppShell>
  );
}
