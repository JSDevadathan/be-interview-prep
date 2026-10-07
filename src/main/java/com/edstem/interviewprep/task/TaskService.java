package com.edstem.interviewprep.task;

import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.task.dto.CreateTaskRequest;
import com.edstem.interviewprep.task.dto.TaskResponse;
import com.edstem.interviewprep.task.dto.UpdateTaskRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final String RESOURCE_NAME = "Task";
    private static final TaskStatus DEFAULT_STATUS = TaskStatus.TODO;

    private final TaskRepository taskRepository;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, Clock clock) {
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        Task task = new Task(
                request.title(),
                request.description(),
                Objects.requireNonNullElse(request.status(), DEFAULT_STATUS),
                request.dueDate(),
                Instant.now(clock));
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<Task> tasks = status == null
                ? taskRepository.findAllByOrderByIdAsc()
                : taskRepository.findByStatusOrderByIdAsc(status);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return TaskResponse.from(findTask(id));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        Task task = findTask(id);
        task.update(request.title(), request.description(), request.status(), request.dueDate());
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.delete(findTask(id));
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
    }
}
